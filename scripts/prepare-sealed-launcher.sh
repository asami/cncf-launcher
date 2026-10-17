#!/usr/bin/env bash
set -euo pipefail

warehouse=""
output=""

usage() {
  echo "Usage: $(basename "$0") --warehouse <task-private-warehouse> --output <sealed-launcher-output>" >&2
}

require_value() {
  local option="$1"
  local value="$2"

  if [[ -z "$value" ]]; then
    echo "$option requires a value." >&2
    exit 1
  fi
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --warehouse)
      require_value "$1" "${2:-}"
      warehouse="$2"
      shift 2
      ;;
    --output)
      require_value "$1" "${2:-}"
      output="$2"
      shift 2
      ;;
    *)
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$warehouse" || -z "$output" ]]; then
  usage
  exit 1
fi
if [[ ! -d "$warehouse" ]]; then
  echo "--warehouse must identify an existing task-private warehouse directory." >&2
  exit 1
fi
if [[ ! -d "$warehouse/repository/ivy2/local" ]]; then
  echo "The task-private warehouse must contain repository/ivy2/local/." >&2
  exit 1
fi
if [[ ! -f "$warehouse/repository/textus/coursier-channel.json" ]]; then
  echo "The task-private warehouse must contain repository/textus/coursier-channel.json." >&2
  exit 1
fi
if ! command -v cs >/dev/null 2>&1; then
  echo "Coursier cs is required to prepare a sealed launcher closure." >&2
  exit 1
fi

warehouse="$(cd "$warehouse" && pwd -P)"
output_parent="$(cd "$(dirname "$output")" && pwd -P)"
output="$output_parent/$(basename "$output")"
if [[ -e "$output" ]]; then
  echo "--output must not already exist: $output" >&2
  exit 1
fi

stage="$(mktemp -d "$output_parent/.sealed-launcher.XXXXXX")"
cleanup_stage() {
  rm -rf "$stage"
}
trap cleanup_stage EXIT INT TERM

mkdir -p "$stage/bin"
cs bootstrap --standalone cncf \
  -r "ivy:file://$warehouse/repository/ivy2/local/[defaultPattern]" \
  --channel "file://$warehouse/repository/textus/coursier-channel.json" \
  --output "$stage/bin/cncf"

if [[ ! -f "$stage/bin/cncf" || ! -x "$stage/bin/cncf" ]]; then
  echo "Coursier did not create an executable cncf closure." >&2
  exit 1
fi

verification_home="$stage/verification-home"
verification_cache="$stage/verification-coursier-cache"
mkdir -p "$verification_home" "$verification_cache"
if ! HOME="$verification_home" \
  COURSIER_CACHE="$verification_cache" \
  COURSIER_MODE=offline \
  "$stage/bin/cncf" launcher version >/dev/null; then
  echo "The sealed cncf executable cannot start with an empty offline Coursier environment." >&2
  exit 1
fi

digest="$(shasum -a 256 "$stage/bin/cncf" | awk '{print $1}')"
printf '{"executable":"bin/cncf","schemaVersion":1,"sha256":"%s"}\n' "$digest" \
  > "$stage/sealed-launcher.json"

mv "$stage" "$output"
stage=""
trap - EXIT INT TERM
printf 'sealed_launcher=%s executable=bin/cncf sha256=%s\n' "$output" "$digest"

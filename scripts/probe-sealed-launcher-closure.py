#!/usr/bin/env python3

"""Static executable specification for the sealed cncf launcher closure."""

import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


MANIFEST_NAME = "sealed-launcher.json"
EXECUTABLE_PATH = "bin/cncf"


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def _within(root: Path, candidate: Path) -> bool:
    return candidate == root or root in candidate.parents


def _read_closure(root: Path) -> None:
    if not root.is_dir():
        raise ValueError("sealed launcher root is missing")
    manifest_path = root / MANIFEST_NAME
    if not manifest_path.is_file():
        raise ValueError("sealed launcher manifest is missing")
    try:
        manifest = json.loads(manifest_path.read_text())
    except json.JSONDecodeError as error:
        raise ValueError("sealed launcher manifest is malformed") from error
    if not isinstance(manifest, dict):
        raise ValueError("sealed launcher manifest must be an object")
    if set(manifest) != {"executable", "schemaVersion", "sha256"}:
        raise ValueError("sealed launcher manifest shape is invalid")
    if manifest["schemaVersion"] != 1:
        raise ValueError("sealed launcher manifest schema is invalid")
    if manifest["executable"] != EXECUTABLE_PATH:
        raise ValueError("sealed launcher executable path is invalid")
    digest = manifest["sha256"]
    if not isinstance(digest, str) or len(digest) != 64 or any(
        character not in "0123456789abcdef" for character in digest.lower()
    ):
        raise ValueError("sealed launcher digest shape is invalid")
    relative_path = Path(manifest["executable"])
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise ValueError("sealed launcher executable path traverses its root")
    canonical_root = root.resolve(strict=True)
    executable_path = (canonical_root / relative_path).resolve(strict=True)
    if not _within(canonical_root, executable_path):
        raise ValueError("sealed launcher executable escapes its root")
    if not executable_path.is_file() or not os.access(executable_path, os.X_OK):
        raise ValueError("sealed launcher executable is not runnable")
    actual = hashlib.sha256(executable_path.read_bytes()).hexdigest()
    if actual != digest.lower():
        raise ValueError("sealed launcher executable digest does not match")


def _expect_rejection(root: Path, expected: str) -> None:
    try:
        _read_closure(root)
    except ValueError as error:
        _require(expected in str(error), f"Expected {expected!r}, got {str(error)!r}")
    else:
        _require(False, f"Malformed closure was accepted: {root}")


def _write_valid_closure(root: Path) -> Path:
    executable = root / EXECUTABLE_PATH
    executable.parent.mkdir(parents=True)
    executable.write_text(
        "#!/bin/sh\n"
        "if [ \"${1:-}\" = \"launcher\" ] && [ \"${2:-}\" = \"version\" ]; then\n"
        "  printf '%s\\n' 'sealed cncf fixture version'\n"
        "  exit 0\n"
        "fi\n"
        "printf '%s\\n' 'expected launcher version' >&2\n"
        "exit 2\n"
    )
    executable.chmod(0o755)
    digest = hashlib.sha256(executable.read_bytes()).hexdigest()
    (root / MANIFEST_NAME).write_text(
        json.dumps(
            {"executable": EXECUTABLE_PATH, "schemaVersion": 1, "sha256": digest},
            separators=(",", ":"),
        )
        + "\n"
    )
    return executable


def _verify_preparation_source(script: str) -> None:
    for required in (
        "--warehouse",
        "--output",
        "repository/ivy2/local",
        "repository/textus/coursier-channel.json",
        'mkdir -p "$stage/bin"',
        "cs bootstrap --standalone cncf",
        "--channel",
        '--output "$stage/bin/cncf"',
        "COURSIER_MODE=offline",
        "COURSIER_CACHE=\"$verification_cache\"",
        "HOME=\"$verification_home\"",
        '"$stage/bin/cncf" launcher version',
        "sealed-launcher.json",
        '"bin/cncf"',
    ):
        _require(required in script, f"Preparation contract is missing {required!r}")
    _require("cs install" not in script, "Preparation must not use cs install")
    _require("sbt" not in script.lower(), "Preparation must not run SBT")


def _expect_preparation_source_rejection(script: str, expected: str) -> None:
    try:
        _verify_preparation_source(script)
    except AssertionError as error:
        _require(expected in str(error), f"Expected {expected!r}, got {str(error)!r}")
    else:
        _require(False, "Malformed preparation source was accepted")


def _verify_preparation_contract(script_path: Path) -> None:
    script = script_path.read_text()

    # Given the preparation source without standalone bootstrap.
    # When the executable specification checks its sealed-closure contract.
    # Then it rejects a launcher source that can be thin or host-cache dependent.
    _expect_preparation_source_rejection(
        script.replace("cs bootstrap --standalone cncf", "cs bootstrap cncf"),
        "cs bootstrap --standalone cncf",
    )

    # Given the preparation source with a deprecated Coursier install command.
    # When the executable specification checks its sealed-closure contract.
    # Then it rejects a runtime-thin launcher installation path.
    _expect_preparation_source_rejection(
        script + "\ncs install cncf\n",
        "must not use cs install",
    )

    # Given the actual sealed launcher preparation source.
    # When the executable specification checks its preparation contract.
    # Then it requires standalone bootstrap and empty-cache offline startup validation.
    _verify_preparation_source(script)


def _verify_manifest_admission() -> None:
    with tempfile.TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory)

        # Given a missing closure root.
        # When manifest admission reads that root.
        # Then admission rejects the missing root.
        _expect_rejection(root / "missing-root", "missing")

        # Given a closure root without its root manifest.
        # When manifest admission reads the closure.
        # Then admission rejects the missing manifest.
        missing_manifest = root / "missing-manifest"
        missing_manifest.mkdir()
        _expect_rejection(missing_manifest, "manifest is missing")

        # Given a closure root with malformed manifest JSON.
        # When manifest admission parses the manifest.
        # Then admission rejects the malformed manifest.
        malformed = root / "malformed"
        malformed.mkdir()
        (malformed / MANIFEST_NAME).write_text("{")
        _expect_rejection(malformed, "manifest is malformed")

        # Given a closure manifest with an incomplete schema.
        # When manifest admission validates its fields.
        # Then admission rejects the invalid shape.
        wrong_shape = root / "wrong-shape"
        wrong_shape.mkdir()
        (wrong_shape / MANIFEST_NAME).write_text('{"schemaVersion":1}\n')
        _expect_rejection(wrong_shape, "manifest shape")

        # Given a manifest that traverses outside its closure root.
        # When manifest admission resolves the executable path.
        # Then admission rejects traversal before reading an executable.
        traversal = root / "traversal"
        traversal.mkdir()
        (traversal / MANIFEST_NAME).write_text(
            '{"executable":"../outside","schemaVersion":1,"sha256":"' + "0" * 64 + '"}\n'
        )
        _expect_rejection(traversal, "executable path is invalid")

        # Given a manifest whose executable digest is not the sealed file digest.
        # When manifest admission verifies the executable.
        # Then admission rejects the digest mismatch.
        bad_digest = root / "bad-digest"
        _write_valid_closure(bad_digest)
        manifest = json.loads((bad_digest / MANIFEST_NAME).read_text())
        manifest["sha256"] = "0" * 64
        (bad_digest / MANIFEST_NAME).write_text(json.dumps(manifest, separators=(",", ":")) + "\n")
        _expect_rejection(bad_digest, "digest does not match")

        # Given a digest-matching regular file without execute permission.
        # When manifest admission requires the direct launcher executable.
        # Then admission rejects the non-runnable closure rather than a source-token fixture.
        non_runnable = root / "non-runnable"
        executable = _write_valid_closure(non_runnable)
        executable.chmod(0o644)
        _expect_rejection(non_runnable, "not runnable")

        # Given a behavior-bearing executable fixture that serves its launcher version.
        # When the direct executable is invoked through its non-runtime launcher path.
        # Then it proves the admitted fixture is executable behavior, not an exit-0 token.
        valid = root / "valid"
        executable = _write_valid_closure(valid)
        completed = subprocess.run([str(executable), "launcher", "version"], capture_output=True, text=True, check=False)
        _require(completed.returncode == 0, f"Behavior-bearing executable did not serve launcher version: {completed.stderr}")
        _require("sealed cncf fixture version" in completed.stdout, "Executable fixture did not expose launcher-version behavior")
        _read_closure(valid)


def main() -> int:
    script_path = Path(__file__).with_name("prepare-sealed-launcher.sh")
    try:
        _verify_preparation_contract(script_path)
        _verify_manifest_admission()
    except (AssertionError, OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"SEALED_LAUNCHER_CLOSURE_CONTRACT_FAILED: {error}", file=sys.stderr)
        return 1
    print("sealed_launcher=manifest-validated preparation=standalone-bootstrap")
    print("SEALED_LAUNCHER_CLOSURE_CONTRACT_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

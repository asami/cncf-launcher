# Prebuilt runtime bundle input

## Status

This specification defines the direct-execute `--runtime-bundle-dir <dir>`
input for the `cncf` launcher.

## Command contract

`--runtime-bundle-dir <dir>` is a global option accepted only for direct
`command`, `server`, and `client` execution. It is not launcher configuration
and `install-cli` must reject it; generated development commands never record
or install it.

A bundle option is mutually exclusive with `--runtime`, `--runtime-selection`,
`--runtime-no-compatible`, and `--runtime-dev-dir`. The launcher rejects every
such combination before runtime selection. In the absence of the bundle option,
existing runtime-selection and development-runtime behavior remains unchanged.

## Sealed bundle layout

The selected directory is canonicalized. It must contain:

```text
<bundle>/
  lib/*.jar
  target/cncf.d/runtime-classpath.txt
```

`runtime-classpath.txt` uses the platform classpath separator. Every entry must
name an existing regular JAR whose canonical path is beneath the canonical
`<bundle>/lib` directory. Empty entries, directories, non-JAR files, missing
files, malformed paths, external paths, and symlink escapes are rejected. A
bundle cannot substitute source output or a development project classpath.

Exactly one JAR in this sealed set must contain `META-INF/cncf/runtime.yaml`.
That descriptor must parse as YAML with `schemaVersion: 1`, `runtime: cncf`, and
a nonempty `version`. The descriptor version is the bundle runtime version.

## Execution contract

Before `CncfInvoker` receives a classpath, the launcher derives component
runtime requirements as usual and rejects a bundle descriptor version that does
not satisfy them. A valid bundle invokes the runtime only with its sealed JAR
classpath. It does not consult Coursier, a runtime catalog, a development
runtime classpath, a `build.sbt`, or any fallback runtime source.

## C3 sealed launcher closure

The C3 sealed launcher closure is a separate consumer input from the C1
`--runtime-bundle-dir` bundle. Its preparation contract is:

```text
scripts/prepare-sealed-launcher.sh \
  --warehouse <task-private-warehouse> \
  --output <absent-task-private-closure>
```

The warehouse must already provide `repository/ivy2/local/` and
`repository/textus/coursier-channel.json`. Preparation uses that channel only
to run `cs bootstrap --standalone` for the launcher; it must not run SBT. It
then starts the direct executable through `launcher version` with a newly empty HOME and
Coursier cache in offline mode before it creates exactly this runtime-facing
contract:

```text
<closure>/
  bin/cncf
  sealed-launcher.json
```

`sealed-launcher.json` is canonical JSON with exactly these fields:

```json
{"executable":"bin/cncf","schemaVersion":1,"sha256":"<64-lowercase-or-uppercase-hex>"}
```

Consumers must require an explicit task-private closure root and SHA-256 of
the root manifest, canonicalize the root, reject source checkout, `target`,
and default shared warehouse/cache locations, verify the manifest shape and
digest, then execute `bin/cncf` directly. They must not resolve, install,
cache, or contact a network through Coursier at runtime. The root manifest
digest binds the standalone executable closure. The C3 closure remains
compatible with the C1 bundle; the former selects the executable and the latter
supplies its sealed runtime.

# Prebuilt runtime bundle design

The launcher models a prebuilt runtime bundle as a separate direct-execution
input rather than as a runtime selector or a development checkout. This keeps
the public lifecycle intentionally narrow: callers name a bundle for one
execution, while launcher configuration and installed development commands
continue to describe their existing runtime mechanisms.

`CncfRuntimeBundle` canonicalizes and seals the input before runtime selection.
It reads the established classpath-file grammar from
`target/cncf.d/runtime-classpath.txt`, constrains every entry to canonical JARs
under `lib`, and finds one embedded runtime descriptor. This creates one
trusted pair: the descriptor version and the exact JAR classpath.

`CncfLauncher` validates that descriptor version against component requirements
before it hands the sealed classpath to `CncfInvoker`. The bundle branch has no
resolver or development-classpath call, so it cannot resolve, compile, or fall
back to another runtime. The ordinary branch is retained unchanged when no
bundle is supplied.

## Sealed launcher closure

The prebuilt runtime bundle is the C1 direct-execution input. It is not the
C3 launcher closure: C1 supplies the runtime JAR classpath and descriptor,
while C3 supplies the already-installed `cncf` executable that starts that
bundle.

`scripts/prepare-sealed-launcher.sh` receives an explicit task-private
warehouse and an absent output directory. Preparation verifies the warehouse's
local Ivy source and Coursier channel, uses `cs bootstrap --standalone` only at
preparation time, and writes a self-contained closure with `bin/cncf` plus
`sealed-launcher.json`. Before sealing, preparation starts that direct
executable through its non-runtime `launcher version` path with a newly empty HOME and
Coursier cache in offline mode; failure to start rejects preparation. The
manifest is deterministic JSON with schema version 1, the fixed relative
executable path, and that executable's SHA-256 digest.

At runtime a consumer mounts the closure read-only, verifies the manifest
before container ownership checks, and executes `bin/cncf` directly. Runtime
has no Coursier cache or network resolution/install path, does not run SBT, and
has no launcher/source fallback. This C3 contract is preparation and
static-admission design only; it does not by itself claim a packaged-runtime
acceptance session.

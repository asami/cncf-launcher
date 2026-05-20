# cncf

Developer launcher for CNCF component development.

`textus` is the user/operator launcher. `cncf` is intentionally focused on
the component development loop: resolving a CNCF runtime, preparing component
runtime classpaths, checking development directories, and starting the CNCF
server against local component sources.

## Usage

```bash
cncf runtime current
cncf runtime refresh
cncf runtime use recommended
cncf runtime use latest
cncf runtime use newest

cncf dev classpath
cncf dev check
cncf dev server
cncf dev server --runtime-dev-dir ../cncf
cncf dev server-emulation my-component.my-service.my-operation
```

`cncf dev classpath` writes:

```text
target/cncf.d/runtime-classpath.txt
```

`cncf dev server` invokes `org.goldenport.cncf.CncfMain` in the same JVM. It is
the development-project launcher: `--project <dir>` selects the main target,
and the current directory is the default main target. The main target is not
resolved from CAR/SAR repositories in dev mode, even when `project.yaml` says
`packaging.kind: car`.

The main target uses `target/cncf.d/runtime-classpath.txt`. If the file is
missing or empty, `cncf dev server`, `cncf dev client`, `cncf dev command`, and
`cncf dev server-emulation` prepare it automatically from `Runtime /
fullClasspath`. Use `cncf dev classpath --project <dir>` to prepare it manually.

Dependency components are separate from the main target. Use
`--component-dev-dir <dir>` or `.cncf/config.yaml` `dev.componentDevDirs` for
dependencies that are also under local development. Dependencies without local
overrides are resolved by the CNCF runtime from configured component
repositories. `textus server <artifact>` is the CAR/SAR artifact launcher for
repository-based application startup.

Use `--runtime-dev-dir <dir>` or `runtime.devDir` to run against a local CNCF
runtime checkout instead of a published runtime artifact. This is for CNCF core
development; component source directories still use `--component-dev-dir`.

Runtime arguments placed before the operation selector are forwarded before
`server`, `client`, or `command`, for example `cncf dev command --repository-dir
repository.d minimal.main.hello`. Use `--no-project-classpath` when invoking
packaged CAR/SAR artifacts without the current project classpath.

## Configuration

The launcher reads only:

```text
~/.cncf/config.yaml
$PWD/.cncf/config.yaml
```

For `cncf dev ... --project <dir>`, the project config is `<dir>/.cncf/config.yaml`.

Example:

```yaml
runtime:
  version: recommended
  devDir: ../cncf
  catalog:
    url: https://www.simplemodeling.org/repository/textus/runtime-catalog.yaml

dev:
  project: .
  port: 19532
  componentDevDirs:
    - ../textus-user-account
    - ../textus-user-notification

repositories:
  maven:
    - https://www.simplemodeling.org/repository/maven
```

`.textus/config.yaml` remains a CNCF runtime/project configuration file. It is
not read as launcher configuration.

Runtime version selection is the same model as `textus`:

1. `--runtime-dev-dir <dir>` / `runtime.devDir` for dev commands only
2. `--runtime <version>`
3. `$PWD/.cncf/version`
4. `~/.cncf/version`
5. `recommended`

`cncf runtime use <version>` writes project scope when the current directory
already has `.cncf/`; otherwise it writes global scope. Use `--project` or
`--global` to force the target.

Runtime selector terms are:

- `recommended`: operator-selected default runtime from the catalog.
- `latest`: alias of `latest-stable`; the newest stable runtime in the catalog.
- `latest-stable`: newest stable runtime in the catalog.
- `latest-snapshot`: newest snapshot runtime in the catalog.
- `newest`: newest enabled runtime across all catalog channels.

When a development project or local dependency project declares `runtime.cncf`
compatibility, `cncf dev` uses `current-compatible` selection by default. Use
`--runtime-selection=tested-latest`, `--runtime-selection=latest`, or
`--runtime-selection=newest` to choose a different compatible-runtime policy.

The runtime selector and compatibility-selection semantics are intentionally
duplicated in the `textus` and `cncf` launchers instead of being factored into a
shared launcher-core library. These launchers are small, separately distributed
entrypoints and are expected to stabilize. When changing `recommended`,
`latest`, `newest`, or `runtime.cncf` compatibility behavior, update both
launchers and their tests together.

The Coursier app entry is published into the same channel file as `textus`:

```text
https://www.simplemodeling.org/repository/textus/coursier-channel.json
```

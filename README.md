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
cncf dev server --project-dev ../textus-knowledge-editor
cncf dev server --name textus-knowledge-editor:0.1.0-SNAPSHOT
cncf dev server --car-file target/textus-knowledge-editor-0.1.0-SNAPSHOT.car
cncf dev server-emulation my-component.my-service.my-operation
```

`cncf dev classpath` writes:

```text
target/cncf.d/runtime-classpath.txt
```

`cncf dev server` invokes `org.goldenport.cncf.CncfMain` in the same JVM. It
defaults to `--project-dev .`, meaning the current development directory is
the main target. Use `--project-dev <dir>` to select another development
project. The project-dev target is not resolved from CAR/SAR repositories in
dev mode, even when `project.yaml` says `packaging.kind: car`.

Packaged targets are explicit. Use `--name <artifact>[:<version>]` for a
repository/local artifact, `--car-file <file>` for a direct CAR/SAR file, or
`--project-car <dir>` for a CAR/SAR already generated under a project target
directory. Target options are mutually exclusive.

The main target uses `target/cncf.d/runtime-classpath.txt`. If the file is
missing or empty, `cncf dev server`, `cncf dev client`, `cncf dev command`, and
`cncf dev server-emulation` prepare it automatically from `Runtime /
fullClasspath`. Use `cncf dev classpath --project-dev <dir>` to prepare it
manually.

Dependency components are separate from the main target. Use
`--component-dev-dir <dir>` or `conf/cncf/launcher.yaml` `dev.component-dev-dirs` for
source-level debugging of dependencies that are also under local development.
For the normal dependency-component development loop, run `sbt
cozyPublishLocalCar` in the dependency component. This publishes the CAR,
catalog, and metadata into `~/.cncf/local`, and the `cncf` launcher passes
that local repository to the runtime before public repositories. Dependencies
without source overrides are resolved by the CNCF runtime from configured
component repositories. `textus server <artifact>` is the CAR/SAR artifact
launcher for repository-based application startup.

Use `--runtime-dev-dir <dir>` or `runtime.devDir` to run against a local CNCF
runtime checkout instead of a published runtime artifact. This is for CNCF core
development; component source directories still use `--component-dev-dir`.

Runtime arguments placed before the operation selector are forwarded before
`server`, `client`, or `command`, for example `cncf dev command --repository-dir
repository.d minimal.main.hello`. Use `--no-project-classpath` when invoking
packaged CAR/SAR artifacts without the current project classpath.

## Launcher Configuration

The `cncf` launcher reads launcher configuration from:

```text
~/.cncf/launcher.yaml
$PWD/conf/cncf/launcher.yaml
$PWD/.cncf/launcher.yaml
```

For `cncf dev ... --project-dev <dir>`, the project launcher config is
`<dir>/conf/cncf/launcher.yaml`, with `<dir>/.cncf/launcher.yaml` as a local
override. Use `--config <file>` for an additional launcher config file, for
example:

```bash
cncf --config etc/launcher/debug.yaml dev server
```

Launcher config is intentionally lightweight. It supports `yaml` / `yml`,
`properties` / `props`, and lightweight `conf` files with dotted keys. JSON,
XML, and full HOCON are CNCF runtime config formats, not launcher config
formats.

Example:

```yaml
cncf:
  launcher:
    dev:
      dir: <cncf-launcher-checkout>

runtime:
  version: recommended
  dev-dir: ../cncf
  catalog:
    url: https://www.simplemodeling.org/repository/textus/runtime-catalog.yaml

dev:
  project-dev: .
  port: 19532
  component-dev-dirs:
    - ../textus-user-account
    - ../textus-user-notification

repositories:
  maven:
    - https://www.simplemodeling.org/repository/maven
```

`cncf.launcher.dev.dir` is for the launcher itself. When an installed `cncf`
supports this key, it delegates to the development launcher in that checkout
and passes the original command line through. The delegated launcher is marked
internally so it does not recursively delegate again.

`runtime.dev-dir` is different: it selects the CNCF runtime checkout used by
`cncf dev ...` commands after the launcher has started.

## CNCF Runtime Configuration

CNCF runtime configuration is separate from launcher configuration. Runtime
configuration is read by the CNCF runtime after the launcher has selected and
started it. Use `conf/cncf/config.yaml` for Git-managed project runtime
configuration and `.cncf/config.yaml` for local override configuration. You can
also pass an explicit runtime config file through the launcher:

```bash
cncf --config etc/launcher/debug.yaml --cncf-config etc/debug.yaml dev server
```

`--cncf-config <file>` is forwarded to the runtime as
`--cncf.config.files=<file>`. The `cncf` command is a component-developer tool,
so its explicit runtime-config option uses the `cncf` spelling. The user-facing
`textus` launcher uses Textus-oriented configuration names.

The default CAR/SAR repository order is:

1. CLI/config explicit repositories
2. `~/.cncf/local/repository/car` and `~/.cncf/local/repository/sar`
3. `~/.cncf/cache/car` and `~/.cncf/cache/sar`
4. runtime catalog repositories
5. built-in SimpleModeling.org repositories

`~/.cncf/local` is developer-owned local publish state produced by
`sbt cozyPublishLocalCar` / `sbt cozyPublishLocalSar`. `~/.cncf/cache` is
runtime-managed remote artifact cache and can be deleted without removing
locally published development artifacts. Snapshot components are local-only by
default; if a snapshot is missing, publish it locally instead of expecting
public/cache lookup.

`component.d` and `repository.d` are not used implicitly by `cncf dev server`.
Configure repositories explicitly or publish dependency components to
`~/.cncf/local`.

`conf/cozy/config.yaml` and `.cozy/config.yaml` belong to build/publish
operation defaults.
`conf/cncf/launcher.yaml` and `.cncf/launcher.yaml` belong to the `cncf`
launcher.
`conf/cncf/config.yaml`, `.cncf/config.yaml`, and `.textus/config.yaml` belong
to CNCF runtime configuration.
`project.yaml` belongs to artifact metadata and runtime compatibility.

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

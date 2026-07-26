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

cncf repository list
cncf repository list --kind car --include-development
cncf repository show textus-blog --kind car
cncf repository show ../textus-blog

cncf /Users/asami/src/dev2026/textus-sanpomap command validate-presentation --presentationDsl presentation-dsl.yaml
cncf . server
cncf textus-sanpomap:0.2.0-SNAPSHOT client
```

`cncf repository list` and `cncf repository show` inspect the machine-local
CAR/SAR catalog under `~/.cncf/local`. Development checkouts are excluded by
default. Admit the current checkout with `--include-development`, admit other
checkouts explicitly with repeatable `--development-dir <dir>`, or pass a
component directory directly to `repository show`. Development identity comes
from `project.yaml`, not the directory name, and overrides a matching local
artifact while preserving the shared six-column repository output contract.

`cncf dev ...` is deprecated. Use target-first `command`, `server`, and
`client` syntax. `cncf dev` remains only as a compatibility alias and is not
the supported component-development entry point.

For target-first `server` execution, the launcher forwards artifact activation
without assigning a port. CNCF runtime reads `textus.server.default-port` from
the CAR/SAR descriptor, coordinates the machine-local assignment, and selects
an additional-instance port when necessary. `textus.server.port` remains an
explicit operator override and may be supplied as a runtime property.

## Development CLI Installation

`cncf install-cli` installs a development command that delegates to
`cncf <fixed-target> command` against a local project checkout. This is intended for
component developers. General users should use `textus install-cli`, which
delegates to packaged CAR/SAR artifacts.

```bash
cncf --runtime-dev-dir /Users/asami/src/dev2025/cloud-native-component-framework install-cli sanpomap \
  --project-dev /Users/asami/src/dev2026/textus-sanpomap \
  --component-dev-dir /Users/asami/src/dev2026/textus-georesolver \
  --overwrite

sanpomap-dev validate-presentation --presentationDsl xxx.yaml --format yaml
```

The installed command name is suffixed with `-dev` unless the requested base
name already ends in `-dev`. The generated command delegates to
`cncf <fixed-target> command <operation-selector>` and passes user arguments through as
the operation selector and operation parameters. The target is resolved to an absolute
project path at install time. `~/bin` is the default install
directory; use `--bin-dir <dir>` to choose another location.

Without an explicit runtime option, `install-cli` leaves runtime selection out
of the wrapper. Each invocation therefore follows `launcher.yaml`, `.cncf/version`,
and `~/.cncf/version`, including switching a configured development runtime on
or off after the command has been installed.

When `--runtime <version>` is supplied, `install-cli` pins that version in the
wrapper. When `--runtime-dev-dir` is supplied, it pins that absolute runtime
directory. The launcher reads a development runtime version from its
`build.sbt` and checks it against the main and development dependency component
requirements. This path does not select a version from the runtime catalog.

Leaf-only operation selectors such as `validate-presentation` depend on CNCF
runtime selector resolution. If the pinned runtime requires a full selector,
use `sanpomap-dev sanpomap.presentation.validate-presentation ...` or install a
prefixed development command.

If a project still needs a selector prefix in the generated command, pass
`--operation-prefix <component.service>`. Otherwise the selector is passed
unchanged and resolution is left to the CNCF runtime.

The deprecated `cncf dev classpath` command writes:

```text
target/cncf.d/runtime-classpath.txt
```

The deprecated `cncf dev server` invokes `org.goldenport.cncf.CncfMain` in the same JVM. It
defaults to `--project-dev .`, meaning the current development directory is
the main target. Use `--project-dev <dir>` to select another development
project. The project-dev target is not resolved from CAR/SAR repositories in
dev mode, even when `project.yaml` says `packaging.kind: car`.

Packaged targets are explicit. Use `--name <artifact>[:<version>]` for a
repository/local artifact, `--car-file <file>` for a direct CAR/SAR file, or
`--project-car <dir>` for a CAR/SAR already generated under a project target
directory. Target options are mutually exclusive.

The main target uses `target/cncf.d/runtime-classpath.txt`. If the file is
missing or empty, a development server/client/command invocation reports the
missing classpath and does not start SBT. Use `cncf dev classpath --project-dev
<dir>` as an explicit preparation step.

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

Use `--runtime-dev-dir <dir>` or `runtime.dev-dir` to run against a local CNCF
runtime checkout instead of a published runtime artifact. This applies to
target-first commands and development CLI installation. It is for CNCF core
development; component source directories still use `--component-dev-dir`.

Runtime arguments placed before the operation selector are forwarded before
`server`, `client`, or `command`, for example `cncf . command --repository-dir
repository.d minimal.main.hello`. Use `--no-project-classpath` when invoking
packaged CAR/SAR artifacts without the current project classpath.

## Launcher Configuration

The `cncf` launcher reads launcher configuration from:

```text
~/.cncf/launcher.yaml
ancestor conf/cncf/launcher.yaml and .cncf/launcher.yaml files, outermost first
$PWD/conf/cncf/launcher.yaml
$PWD/.cncf/launcher.yaml
```

Ancestor discovery lets a repository such as `cncf-samples` keep one root
`.cncf/launcher.yaml` for every nested sample directory. A nested directory can
still override the inherited settings with its own `conf/cncf/launcher.yaml` or
`.cncf/launcher.yaml`.

For deprecated `cncf dev ... --project-dev <dir>`, the project launcher config is
`<dir>/conf/cncf/launcher.yaml`, with `<dir>/.cncf/launcher.yaml` as a local
override. Use `--config <file>` for an additional launcher config file, for
example:

```bash
cncf --config etc/launcher/debug.yaml . server
```

Launcher config is intentionally lightweight. It supports `yaml` / `yml`,
`properties` / `props`, and lightweight `conf` files with dotted keys. JSON,
XML, and full HOCON are CNCF runtime config formats, not launcher config
formats.

Example:

```yaml
development:
  enabled: true
  launcher:
    dev-dir: /Users/asami/src/dev2026/cncf-launcher
  runtime:
    dev-dir: /Users/asami/src/dev2025/cloud-native-component-framework

runtime:
  version: recommended
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

`development.enabled` is the single switch for the launcher and runtime
development checkouts in this file. Set it to `false` to keep the configured
candidate directories while using the installed launcher and selected
published runtime. No shell environment switch is required.

```yaml
development:
  enabled: false
```

The launcher and runtime sections can override the common switch independently.
This keeps both checkout paths in the file while selecting only one development
implementation:

```yaml
development:
  enabled: false
  launcher:
    enabled: true
    dev-dir: /Users/asami/src/dev2026/cncf-launcher
  runtime:
    enabled: false
    dev-dir: /Users/asami/src/dev2025/cloud-native-component-framework
```

Section `enabled` values take precedence over `development.enabled`.

### Optional Textus Control Center subsystem registration

Textus Control Center can list Subsystem processes started by canonical CNCF server
commands. Registration is opt-in and applies only to `cncf server` and
`cncf <target> server`; deprecated `cncf dev server` is not a registration
source.

```yaml
textus-control-center:
  registration:
    enabled: true
    endpoint: https://admin.example.test/rest/v1/textus-control-center/subsystem-inventory
    token-env: TEXTUS_CONTROL_CENTER_REGISTRATION_TOKEN
    timeout: 2s
    heartbeat-interval: 30s
    host-label: development-a
    # Optional public endpoint override:
    # base-url: https://subsystem.example.test
```

`endpoint` is the Textus Control Center subsystem-inventory operation base URL. The
launcher sends register, heartbeat, and deregister requests with the bearer
credential named by `token-env`; it never writes or prints the credential
value. Requests use the configured bounded timeout. Missing credentials,
authorization rejection, and Textus Control Center outages emit a sanitized warning but
do not prevent the target server from starting.

Registration is an observation channel, not lifecycle authority. Reopened
Phase 4 moves lifecycle ownership to `textus-supervisor`; launcher evidence and
notification remain best effort and never make Control Center availability a
canonical `cncf server` prerequisite.

When `base-url` is omitted, registration waits until the CNCF runtime has bound
and uses its actual loopback endpoint. This includes additional instances
allocated from the dynamic port range. Set `base-url` only when the registered
public endpoint differs from the runtime bind endpoint, such as behind a proxy.

Higher-precedence project launcher configuration may override the global
switch. In normal operation, `launcher.yaml` is the single place that controls
development selection. CLI development-directory options and their direct
environment equivalents remain explicit emergency overrides. Direct
`launcher.dev-dir` and `runtime.dev-dir` configuration also remain always active;
use `development.*` for paths controlled by the switch.

When an effective launcher or runtime development switch is `true`, its
corresponding `dev-dir` is required. A direct environment directory also
satisfies this configuration-time requirement; CLI options may override a valid
selection when the command is processed. Missing directories fail during
launcher configuration instead of silently selecting a published implementation.

`cncf.launcher.dev.dir` is for the launcher itself. When an installed `cncf`
supports this key, it delegates to the development launcher in that checkout
and passes the original command line through. The delegated launcher is marked
internally so it does not recursively delegate again.
The launcher checkout must have a current
`target/cncf.d/runtime-classpath.txt` containing `cncf.launcher.CncfLauncherMain`;
run `sbt --batch compile` and the deprecated `cncf dev classpath` maintenance
command in the launcher checkout
after changing launcher sources. Stale classpath files are rejected before the
delegated process is spawned.

`runtime.dev-dir` is different: it selects the CNCF runtime checkout used by
target-first commands and installed development commands. It also applies to
the deprecated `cncf dev ...` compatibility surface after the launcher has
started.

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

`component.d` and `repository.d` are not used implicitly by deprecated `cncf dev server`.
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

1. `--runtime-dev-dir <dir>` / `runtime.dev-dir`
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
compatibility, deprecated `cncf dev` uses `current-compatible` selection by default. Use
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

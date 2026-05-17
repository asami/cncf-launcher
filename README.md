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

`cncf dev server` invokes `org.goldenport.cncf.CncfMain` in the same JVM and
passes the current project as a CNCF `component-dev-dir`. Existing project
scripts may remain as wrappers during migration.

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

The Coursier app entry is published into the same channel file as `textus`:

```text
https://www.simplemodeling.org/repository/textus/coursier-channel.json
```

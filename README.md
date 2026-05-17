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
cncf dev server-emulation my-component.my-service.my-operation
```

`cncf dev classpath` writes:

```text
target/cncf.d/runtime-classpath.txt
```

`cncf dev server` invokes `org.goldenport.cncf.CncfMain` in the same JVM and
passes the current project as a CNCF `component-dev-dir`. Existing project
scripts may remain as wrappers during migration.

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

1. `--runtime <version>`
2. `$PWD/.cncf/version`
3. `~/.cncf/version`
4. `recommended`

`cncf runtime use <version>` writes project scope when the current directory
already has `.cncf/`; otherwise it writes global scope. Use `--project` or
`--global` to force the target.

The Coursier app entry is published into the same channel file as `textus`:

```text
https://www.simplemodeling.org/repository/textus/coursier-channel.json
```

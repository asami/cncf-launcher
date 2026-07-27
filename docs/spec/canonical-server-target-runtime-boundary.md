# Canonical Server Target and Runtime Boundary

status = normative
scope = CNCF Launcher canonical `command`, `server`, and `client` execution

## Independent Selection Axes

The canonical launcher form is:

```text
cncf [runtime options] <target> <mode> [runtime arguments]
```

`<target>` selects the CAR or SAR to execute. A development directory target
is activated as a component development directory. `.` selects the current
CAR/SAR checkout, and omitting `<target>` retains the supported current-project
shorthand. A positional development directory must contain `project.yaml` and
declare `project.kind` or `packaging.kind` as `car` or `sar`; a CNCF runtime
checkout is not a valid positional component target.

Runtime selection is independent of the target:

1. CLI `--runtime-dev-dir` selects an explicit local CNCF runtime checkout.
2. CLI `--runtime` selects an explicit published or installed runtime version.
3. Launcher-managed configuration and state select a configured development
   runtime, project/global current version, or default compatible runtime.

A CAR `build.sbt`, runtime classpath file, or `project.yaml` never selects the
CNCF runtime. CAR metadata contributes runtime compatibility requirements only.

## Development Server Metadata

For a resolved CAR/SAR development target, Launcher may derive server
conveniences from that target:

- `conf/cncf/assembly-standalone.yaml`;
- `project.component.config.textus.server.default-port`;
- local lifecycle evidence and Textus Control Center registration identity;
- the private standalone Control Center configuration when the resolved
  artifact is `textus-control-center`.

These values describe the selected component server. They do not participate
in runtime version or runtime classpath resolution. An explicit
`--textus.server.port` or `--cncf.server.port` is retained.

## Compatibility Command

The deprecated `cncf dev ...` path remains a compatibility surface. Its
project development target and runtime development directory are separate in
the same way as canonical execution. It must not be used to define different
target/runtime semantics.

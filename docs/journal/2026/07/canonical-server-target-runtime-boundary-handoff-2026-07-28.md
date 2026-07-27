# Canonical Server Target and Runtime Boundary Handoff

date=2026-07-28
status=implementation-handoff
scope=cncf launcher canonical CAR development server execution

## Context

Running the canonical ArtScene development command from its CAR checkout:

```console
cncf . server
```

failed with:

```text
failed to read CNCF runtime development version from /Users/asami/src/dev2026/textus-art-scene/build.sbt
```

The failure shows that CNCF Launcher interpreted the current CAR development
directory as a CNCF runtime development checkout.

The intended command boundary is:

```text
cncf <CAR development target> server
```

The positional target answers what CAR to start. CNCF runtime selection is a
separate launcher responsibility and must normally use runtime information
managed by `cncf`. A CAR supplies runtime compatibility requirements; it does
not select a CNCF runtime checkout or make that checkout part of its contract.
An explicit `--runtime` or `--runtime-dev-dir` remains an operator/developer
override.

## Confirmed History

- Commit `d67637c` on 2026-07-06 added current-project launcher execution.
  `cncf server` and `cncf . server` were expanded to
  `--component-dev-dir=.`. The current directory was therefore the CAR
  development target.
- Commit `e839b11` on 2026-07-19 documented `cncf . server` as the canonical
  command and deprecated `cncf dev ...` as a compatibility path.
- Commit `58a3dca` on 2026-07-24 added
  `_current_project_development_runtime`. For a `server` command, any current
  directory containing both `build.sbt` and `project.yaml` is treated as a
  CNCF runtime development project.
- The same 2026-07-24 change gives that inferred directory precedence over
  explicit and configured runtime development directories in `_run_execute`.
  It then asks `DevSupport.cncfRuntimeClasspath` and
  `_development_runtime_version_from_classpath` to treat the CAR project as
  the runtime.

The 2026-07-24 change is the semantic regression.

## Why the Regression Was Not Seen Earlier

ArtScene introduced `.cncf/launcher.yaml` on 2026-07-06 with separate
development settings:

```yaml
runtime:
  dev-dir: ../../dev2025/cloud-native-component-framework
dev:
  project-dev: .
  profile: local-persistent
  port: 19539
```

The compatibility command `cncf dev server` is parsed as `CncfCommand.Dev` and
uses the `_run_dev` path. In that path the CAR development project and CNCF
runtime development directory remain separate. The regression exists in the
canonical `CncfCommand.Execute` / `_run_execute` path, so regular use of the
old dev mode could hide it.

Once `cncf . server` is used, the current-project inference added on
2026-07-24 precedes `runtime.dev-dir`, so the ArtScene launcher configuration
cannot correct the mistaken runtime choice.

## Required Boundary

The implementation must preserve these independent axes:

1. **Execution target**
   - Positional `<target>` identifies a CAR/SAR artifact, named component, or
     CAR development directory.
   - `.` identifies the current CAR development directory.
   - Omitting the target for `server` continues to mean the current CAR
     development directory when that canonical shorthand is supported.
2. **CNCF runtime**
   - Normal runtime selection uses the current/default/compatible runtime
     information managed by `cncf`.
   - CAR metadata contributes compatibility requirements only.
   - `--runtime` explicitly overrides normal version selection.
   - `--runtime-dev-dir` explicitly selects a local CNCF runtime checkout for
     runtime development.
3. **Development conveniences**
   - CAR descriptor, development profile, default-port, lifecycle evidence,
     and Control Center behavior must be derived from the resolved CAR target
     where applicable.
   - They must not change how the CNCF runtime is selected.

`cncf <CNCF development directory> server` must not be the normal way to
select a runtime. As a positional argument it is a component target and should
be rejected when the directory is not a valid CAR/SAR development target.

## Implementation Handoff

1. Add Executable Specifications that establish the target/runtime separation
   before changing launcher behavior.
2. Remove the current-directory-to-runtime inference from `_run_execute`.
   In particular, `_current_project_development_runtime` must not participate
   in runtime version or runtime classpath selection.
3. Restore runtime resolution to the independent launcher flow:
   explicit runtime development override, explicit runtime version, then
   `cncf`-managed current/default/compatible runtime selection.
4. Resolve development server arguments from the parsed CAR target instead of
   assuming that the process current directory is a CNCF runtime checkout.
5. Preserve the useful lifecycle/evidence work from `58a3dca`, but keep it
   independent of runtime selection.
6. Promote the stable target/runtime contract into launcher spec documentation;
   this journal entry is historical handoff evidence and is not itself
   normative.

## Regression Scenarios

The Executable Specification should cover at least:

- From a CAR checkout, `cncf . server` activates
  `--component-dev-dir=.` and uses the runtime selected by `cncf`.
- From outside a CAR checkout, `cncf <CAR development directory> server`
  activates that directory and uses the runtime selected by `cncf`.
- `cncf server` retains the supported current-CAR shorthand without treating
  the current directory as a runtime checkout.
- `cncf --runtime <version> <CAR development directory> server` uses the
  explicit runtime version.
- `cncf --runtime-dev-dir <CNCF development directory>
  <CAR development directory> server` uses the explicit development runtime
  while retaining the CAR as the execution target.
- A configured runtime selection is not overridden merely because the CAR
  directory contains `build.sbt` and `project.yaml`.
- A CNCF runtime checkout passed as the positional target is not reinterpreted
  as `--runtime-dev-dir`.
- The deprecated `cncf dev server` compatibility path and the canonical
  command agree on target/runtime separation.

## Completion Condition

The handoff is complete when the canonical target-first commands start the
selected CAR development target, runtime selection comes only from CNCF
launcher state or explicit runtime overrides, the regression scenarios pass,
and the stable behavior is represented in both launcher specification
documentation and its Executable Specification.

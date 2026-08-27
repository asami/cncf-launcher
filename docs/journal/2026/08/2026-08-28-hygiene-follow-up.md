# Hygiene Follow-up — 2026-08-28

## HYG-CNCF-LAUNCHER-SOURCE-SIZE

- Status: `CLOSED`
- Resolution: Extracted development-server/configuration and Control Center registration into `CncfLauncherServerSupport`; split the canonical executable specification into command/repository, lifecycle, configuration/runtime, and development/packaging suites while preserving `CncfLauncherSpec` selection. Required validation boundary: `testOnly cncf.launcher.CncfLauncherSpec`, then affected accumulator `test`.
- Discovered: 2026-08-28, `cncf-validated-commit` preparation
- Repository: `cncf-launcher`
- Location: `src/main/scala/cncf/launcher/CncfLauncher.scala`
- Evidence: the target source is 1,086 lines, exceeding the 1,000-line source-size debt threshold.
- Category: source responsibility and reviewability
- Risk: launcher responsibilities are costly to review and changes can couple otherwise independent command/lifecycle behavior.
- Priority: medium
- Outside current boundary: a safe split affects existing launcher collaborators and cannot be completed as a local file extraction within the runtime-registration repair.
- Proposed task: identify cohesive launcher responsibilities and split them under a dedicated compatibility-preserving maintenance task.

## HYG-CNCF-LAUNCHER-SPEC-SIZE

- Status: `CLOSED`
- Resolution: Extracted development-server/configuration and Control Center registration into `CncfLauncherServerSupport`; split the canonical executable specification into command/repository, lifecycle, configuration/runtime, and development/packaging suites while preserving `CncfLauncherSpec` selection. Required validation boundary: `testOnly cncf.launcher.CncfLauncherSpec`, then affected accumulator `test`.
- Discovered: 2026-08-28, `cncf-validated-commit` preparation
- Repository: `cncf-launcher`
- Location: `src/test/scala/cncf/launcher/CncfLauncherSpec.scala`
- Evidence: the executable specification is 5,121 lines and currently executes 123 examples, exceeding both normal specification-size thresholds.
- Category: executable-specification organization
- Risk: the specification chapter is expensive to navigate and bounded behavior changes require broad review.
- Priority: medium
- Outside current boundary: splitting by observable command/lifecycle surface requires coordinated specification restructuring beyond the registration repair.
- Proposed task: divide the specification by stable public behavior surfaces while preserving Given/When/Then coverage and test selection.

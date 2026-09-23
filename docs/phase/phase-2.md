# Phase 2: Platform Subcomponent Installation

## Goal

Add generic launcher support for platform-specific Subcomponents distributed by CAR.

## Scope

- Read platform artifact metadata from CAR.
- Match OS and architecture.
- Resolve bundled artifacts and separate-CAR Subcomponents.
- Extract/stage the selected artifact.
- Provide a clean handoff for platform/application-specific installation.
- Validate with the textus-control-center macOS Menu Bar artifact.

## Service Bus migration direction

- Preserve existing dedicated launcher management/storage as the authoritative current-state source initially.
- Add meaningful lifecycle event publication to CNCF Service Bus in parallel; do not replace storage in the first integration.
- Use the journal for Control Center operational history/timelines as it matures.
- Only after stable operation, migrate duplicated historical-record responsibilities toward Service Bus Journal.
- Do not reconstruct launcher current state from the journal; Event Sourcing is not a goal.

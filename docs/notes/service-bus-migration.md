# Service Bus Migration

cncf-launcher will retain its existing dedicated management/storage area as the authoritative current-state source during initial Service Bus adoption.

## Staged migration

1. Existing mode: dedicated launcher storage remains authoritative.
2. Dual/compatibility mode: continue existing storage and additionally publish meaningful launcher lifecycle events to CNCF Service Bus. Journaled events build authoritative operational history without changing current-state ownership.
3. Stabilized mode: after event vocabulary, persistence, correlation and operational use prove stable, historical/operational record responsibilities may move from dedicated launcher storage to Service Bus Journal.

This is not an Event Sourcing migration. Current installed-CAR state such as current version, installation location, enabled state and configuration may remain in the launcher registry/storage. The migration target is historical/operational facts, not reconstruction of current state from events.

Control Center should gradually prefer Service Bus Journal for timelines/history while querying launcher state for current facts.

# Datastore Profile Boundary

date=2026-07-23
status=active-design-direction
scope=cncf launcher development execution profiles

## Decision

`cncf dev`'s `local-persistent` profile supplies a lightweight SQLite-backed
CNCF datastore binding for local development. It does not authorize an
application component to depend on SQLite, JDBC, SQL, a database path, or a
connection URL.

The launcher owns construction and forwarding of datastore configuration. The
runtime resolves it through `ComponentDataStore`/`DataStoreSpace`; component
behavior uses its internal DSL or persistence port and its admitted component
datastore only.

## Common Database Evolution

The same component behavior must run against a common external datastore when
deployment configuration selects one. The launcher must therefore preserve
component-scoped datastore configuration and must not make the
`local-persistent` SQLite file path part of a CAR's public contract.

When a common datastore hosts multiple components, the launcher/runtime binding
must retain component ownership and collection isolation. It must not offer a
development convenience that lets one component access another component's
data merely because both use the same backend.

## Consequence

Future launcher profile work may add external/shared datastore profiles, but
it must use the same CNCF datastore configuration boundary and retain the
existing local profile only as an implementation choice for development.

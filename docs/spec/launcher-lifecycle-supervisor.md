# Standalone Lifecycle Supervisor

status = in_progress
scope = Textus Control Center Phase 4

`cncf-launcher` owns lifecycle request records and only controls child servers
that it started. It loads its configuration and durable state from the
launcher-owned CNCF home, normally `~/.cncf/`; Control Center never reads those
files directly.

The supervisor accepts authenticated loopback `POST /v1/lifecycle-requests`
requests with `requestId`, `idempotencyKey`, `artifactId`, `action`,
`operatorSubjectId`, and `deadlineAt`. It returns one stable record containing
the request state, safe diagnostic, supervisor ID, and any instance ID.

Identical retries return the original record. Start, stop, and restart reject
any deployment not owned by this supervisor; no operation may search for or
signal an arbitrary PID.

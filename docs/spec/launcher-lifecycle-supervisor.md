# Standalone Lifecycle Supervisor

status = in_progress
scope = Textus Control Center Phase 4

`cncf-launcher` owns lifecycle request records and only controls child servers
that it started. It loads its configuration and durable state from the
launcher-owned CNCF home, normally `~/.cncf/`; Control Center never reads those
files directly.

## Standalone Profile Configuration

The standalone supervisor reads exactly one private profile file:
`~/.cncf/launcher/supervisor.yaml`. Its initial schema admits only explicitly
configured CAR development directories:

```yaml
schema: cncf.launcher.supervisor.v1
profiles:
  development-directory:
    textus-control-center: /absolute/path/to/textus-control-center
```

The mapping key is the CAR `project.name`, not the directory name. Every value
must be an absolute directory with a `project.yaml` declaring the same CAR
identity. A missing, malformed, duplicate, non-absolute, or mismatched entry
is not inferred from the working directory and is reported only as
`supervisor-launch-profile-unavailable`.

The profile file is launcher-private. It is neither a Control Center setting
nor a lifecycle request field, and directory values are never returned through
the supervisor HTTP projection.

## Durable Request Records

The supervisor persists its request/idempotency and owned-instance records in
`~/.cncf/launcher/supervisor-state.json`. A record contains only the protocol
request and its safe result; it contains no credential, directory, command, or
PID. Records are written atomically before the HTTP response is returned.

`GET /v1/lifecycle-requests/{requestId}` returns the same safe result to an
authenticated local caller. A supervisor restart reloads completed/rejected
records for retry and reconciliation, but it never reconstructs process
ownership from a persisted PID, port, command line, or process-table search.

The supervisor accepts authenticated loopback `POST /v1/lifecycle-requests`
requests with `requestId`, `idempotencyKey`, `artifactId`, `action`,
`operatorSubjectId`, and `deadlineAt`. It returns one stable record containing
the request state, safe diagnostic, supervisor ID, and any instance ID.

Identical retries return the original record. Start, stop, and restart reject
any deployment not owned by this supervisor; no operation may search for or
signal an arbitrary PID.

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
identity and a valid `project.component.config.textus.server.default-port`.
The declared port is part of the resolved profile; it is neither supplied by a
caller nor guessed dynamically. A missing, malformed, duplicate,
non-absolute, mismatched, or portless entry is not inferred from the working
directory and is reported only as
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

## Development-Directory Execution and Port Preflight

The supervisor executes an accepted development-directory profile with the
fixed, target-first command:

```text
cncf <absolute-development-directory> server --textus.server.port=<declared-default-port>
```

It starts this command without a shell and retains only the resulting in-memory
child handle. The handle has an opaque supervisor-generated instance ID and is
the sole authority for Stop and Restart; no PID, command line, directory, or
port is persisted as process ownership.

Child standard output and standard error are inherited by the supervisor
process instead of being left in unread pipes, so server logging cannot block a
running child when a pipe buffer fills.

Before Start, the supervisor probes the loopback declared port. An unavailable
port returns `supervisor-port-unavailable` before it spawns a child. Restart
performs the same preflight before stopping its owned child; the owned child's
own matching declared port is permitted, while a failed preflight leaves that
child running. Port probing is a preflight, not a claim of ownership and not a
dynamic-port allocation mechanism.

If a Restart has already stopped its owned child and the replacement cannot
start, the durable request finishes as `failed` and releases that stopped
ownership. A later Start may then create a fresh child; the supervisor never
reassigns the stopped instance or discovers an external process.

If a transition cannot persist its updated state, the supervisor becomes
fail-closed for later lifecycle operations and returns
`supervisor-state-unavailable`. It does not use an in-memory ownership record
to control or reject a later child after the durable ledger has become
unavailable.

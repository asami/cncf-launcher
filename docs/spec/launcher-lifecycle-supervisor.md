# Provisional Launcher Lifecycle Supervisor

status = superseded-by-phase-4-reopen
scope = Textus Control Center Phase 4 transition baseline

This document records the provisional launcher-private supervisor behavior.
It is not the accepted Phase 4 target: `textus-supervisor` becomes lifecycle
authority, embedded by Textus Control Center for standalone use and separately
placeable for future distributed operation. `cncf-launcher` retains durable
common evidence and bounded best-effort notification; it must not own new
lifecycle request, child-ownership, or supervisor configuration state.

The remaining sections describe only the transition baseline that must be
removed or reduced to a compatibility adapter. They are not a contract for new
implementation.

## Transition-Baseline Profile Configuration

The standalone supervisor reads its authority configuration from exactly one
private file: `~/.cncf/launcher/supervisor.yaml`.

```yaml
schema: cncf.launcher.supervisor.v1
supervisor:
  id: local-supervisor
  port: "18014"
  token-env: CNCF_LIFECYCLE_SUPERVISOR_TOKEN
```

The canonical development launch profile is retained from shared Launcher
evidence, not hand-written in this file. A normal invocation from a CAR
checkout establishes it:

```text
cd <development-directory>
cncf server
```

That invocation writes a `development` evidence record below
`~/.cncf/launcher/`. When lifecycle work names the CAR `project.name`, CNCF
Launcher selects the latest matching retained record and revalidates its
absolute directory against `project.yaml`: it must still declare the same CAR
identity and a valid `project.component.config.textus.server.default-port`.
The declared port is part of the resolved profile; it is neither supplied by a
caller nor guessed dynamically. Missing, malformed, non-absolute, mismatched,
or portless evidence is rejected as
`supervisor-launch-profile-unavailable`; Launcher does not search the working
directory or a process table.

Existing `profiles.development-directory.*` entries are accepted only as a
migration fallback for a local installation created by an earlier Phase 4
build. They are never required after the canonical server invocation has left
evidence, and retained evidence takes precedence over them.

The authority file and profile directory are launcher-private. Neither is a
Control Center setting or lifecycle request field, and directory values are
never returned through the supervisor HTTP projection.

`supervisor.id`, `supervisor.port`, and `supervisor.token-env` are required to
host the standalone supervisor. The port is a valid TCP port and the token-env
value is an environment-variable name, never a token value. Unknown keys or an
invalid daemon declaration make the configuration unavailable rather than
falling back to a default endpoint or credential.

## Internal Authority and Diagnostic Foreground Daemon

`cncf launcher lifecycle ensure`, `submit`, and `lookup` are Launcher-internal
Control Center adapters. They resolve the private profile and environment-only
credential, then first probe the authenticated loopback authority. When it is
absent, Launcher starts the foreground host implementation in the background,
waits for its authenticated health response within a bounded interval, and
only then submits or looks up a request. Its log is retained at
`~/.cncf/launcher/supervisor.log`.

The authority readiness interval is bounded to ten seconds. The Control Center
adapter reserves at least twenty seconds for this readiness interval plus the
bounded lifecycle submission, so a normal cold start is not rejected merely
because the authority did not already exist.

This is an implementation detail, not an additional operator workflow. The
normal public starts remain `cncf server` from a development directory and
`textus <artifact> server`. Control Center receives only the safe command
result; it neither reads the private configuration/state files nor knows the
endpoint, credential, or authority PID.

The standalone host command is retained for implementation diagnostics:

```text
cncf launcher supervisor serve
```

It is not the normal launcher interface, is never a Control Center
prerequisite, and must not replace the canonical
development-directory invocation:

```text
cd <development-directory>
cncf server
```

That command remains responsible for recognizing its current directory and for
creating launcher-owned local lifecycle evidence. It may notify a reachable
Control Center, but its evidence does not depend on Control Center being
available.

The diagnostic daemon resolves only `~/.cncf/launcher/supervisor.yaml`, reads the credential from
the configured environment variable, and binds the authenticated supervisor to
loopback. It runs in the foreground until the process is interrupted, then
stops its HTTP listener. It does not daemonize, register an operating-system
service, start a component by itself, or expose a non-loopback endpoint.

## Durable Request Records

The supervisor persists its request/idempotency and owned-instance records in
`~/.cncf/launcher/supervisor-state.json`. A record contains only the protocol
request and its safe result; it contains no credential, directory, command, or
PID. Records are written atomically before the HTTP response is returned.

Launcher uses `GET /v1/lifecycle-requests/{requestId}` internally to return
the same safe result to its bounded caller. A supervisor restart reloads completed/rejected
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

For a supervisor-created child, the supervisor injects that opaque instance ID
as launcher-internal registration metadata. The child `cncf` launcher reuses it
for Control Center registration and every heartbeat, but removes the metadata
before invoking the Textus runtime. Thus a lifecycle result and an observed
runtime instance can be joined by the same ID without treating a rejected,
timed-out, or unavailable lifecycle request as authority over registration or
an independently launched process.

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

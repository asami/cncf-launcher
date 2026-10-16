# Launcher Lifecycle Responsibility

status = active
scope = canonical Launcher observation and retired Phase 4 transition baseline

## Current Responsibility

`cncf server` from the current development directory and `cncf <target> server`
retain their target and runtime resolution. The Launcher writes common durable
start/last-seen/normal-stop evidence independently of Control Center
availability. That evidence is observation and does not create lifecycle
request state, child ownership, a supervisor daemon, or process-control authority.
`textus-supervisor` owns lifecycle execution and durable ownership through the
standard Supervisor SPI consumed by protected Control Center Operations.

The existing opt-in Control Center registration Operations are the notification
surface: protocol version `1`, GET selectors `register-subsystem`,
`heartbeat-subsystem`, and `deregister-subsystem`, with the existing machine
credential and invocation identity. No additional Supervisor Operation or
transport is required.

## Readiness and Bounded Notification

Reporter `start` returns without HTTP on the server startup caller, including
when readiness is already published or a public base is configured. One daemon
scheduled executor per session checks coherent
`textus.server.bound-snapshot` readiness at intervals of at most 50 ms. Only a
version `v1` snapshot with a valid HTTP(S) origin and optional safe `/web` or
`/web/...` application path activates notification; legacy bound-base and
application properties are never combined. A configured valid public origin
replaces the bound origin only after readiness. The optional application URL
uses that same origin, and is omitted when no application path was selected.
The session captures this snapshot once for all reports.

The first request is `register-subsystem` with `starting`. Before acknowledgment,
failed registration is retried only on later configured heartbeat intervals.
After acknowledgment, heartbeat failures retain registration and subsequent
reports remain `running`; they never register as `starting` again. Scheduling
uses fixed delay after each attempt, without catch-up request bursts.

Effective connect and read timeouts each round positive submillisecond settings
up to 1 ms and cap at 5000 ms. Redirects are not followed. An attempt has at
most one connect and one read phase, at most 10 seconds combined at the caps.
Positive submillisecond heartbeat intervals likewise round up to 1 ms.
Failures produce sanitized warnings without credentials, query payloads, or
unrestricted endpoint details.

Shutdown hook and `finally` share cleanup: first close local durable evidence,
then best-effort close registration, isolating each nonfatal failure and
preserving the original server return or exception. Session close is idempotent,
stops periodic work, creates no executor or retry loop, and never waits for an
in-flight register. Before activation it emits no HTTP. An acknowledged session
attempts one terminal `deregister-subsystem`/`stopped`; an in-flight initial
register may finish and perform that sole terminal attempt on its existing
daemon worker. No later register or heartbeat is scheduled.

## Retired Compatibility Commands

The parser shapes `cncf launcher supervisor serve` and
`cncf launcher lifecycle ensure|submit|lookup` remain for safe compatibility
diagnostics. Serve, ensure, and lookup immediately fail with
`launcher-lifecycle-authority-retired`. Submit returns exit code `0` and the
existing safe rejected protocol result echoing the request identity with that
diagnostic code. These routes do not read supervisor configuration or tokens,
ensure or host a daemon, contact an authority, or create lifecycle state.
Operators use protected Control Center lifecycle Operations backed by
`textus-supervisor`. A foreground Launcher daemon is never a prerequisite.

## Historical Transition Baseline (Retired)

The following sections preserve the former prototype's design and internal
implementation contracts as history only. Their descriptions do not grant
current Launcher authority or provide an executable CLI route. Internal
prototype specifications remain historical coverage.

### Transition-Baseline Profile Configuration

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

### Internal Authority and Diagnostic Foreground Daemon

`cncf launcher lifecycle ensure`, `submit`, and `lookup` were prototype
Control Center adapters. They resolved a private profile and environment-only
credential, probed an authenticated loopback authority, and could start the
foreground host in the background before submitting or looking up a request.
That retired flow retained its log at `~/.cncf/launcher/supervisor.log`.

The authority readiness interval is bounded to ten seconds. The Control Center
adapter reserves at least twenty seconds for this readiness interval plus the
bounded lifecycle submission, so a normal cold start is not rejected merely
because the authority did not already exist.

The prototype treated hosting as an internal detail. The
public starts remained `cncf server` from a development directory and
`textus <artifact> server`. Control Center receives only the safe command
result; it neither reads the private configuration/state files nor knows the
endpoint, credential, or authority PID.

The prototype retained this host command for implementation diagnostics:

```text
cncf launcher supervisor serve
```

It was never a normal launcher interface or Control Center prerequisite. Its
execution is now retired; it does not replace the canonical
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

### Durable Request Records

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

### Development-Directory Execution and Port Preflight

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

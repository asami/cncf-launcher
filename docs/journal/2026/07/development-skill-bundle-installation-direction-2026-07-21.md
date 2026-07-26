# CNCF Launcher Development Skill Bundle Direction — 2026-07-21

## Context

`cncf install-cli` already installs an explicit development command against a
local project directory. A CAR's Codex skills also need an unreleased testing
path; requiring `cozyPublishLocalCar` before every skill edit would make the
developer loop unnecessarily slow.

## Direction

CNCF Launcher will add a generic `cncf skill` command family that uses the
same manifest and installed-state rules as Textus Launcher, but resolves an
admitted development directory by default. It installs to project scope by
default, records source-tree/output provenance, and requires an explicit
`--scope user` choice for globally visible development skills.

The implementation must identify stale generated output and source/package
divergence rather than presenting a source bundle as equivalent to a published
CAR. It does not publish, build, or modify the component automatically.

## Documentation Result

The work is planned as CNCF Launcher Phase 1 in
`docs/strategy/cncf-launcher-development-strategy.md` and
`docs/phase/phase-1.md`.

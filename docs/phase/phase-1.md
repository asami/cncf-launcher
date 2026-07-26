# Phase 1: Development Skill Bundle Installation

Status: planned

## Goal

Allow a developer to install and exercise a component's unreleased Codex skill
bundle from a working tree through `cncf`, then later obtain the same bundle
from the published CAR through `textus`.

## Command Surface

```text
cncf skill list [<component-dir|car>]
cncf skill install [<component-dir|car>] [--scope project|user] [--configure-mcp]
cncf skill status <bundle-or-component>
cncf skill update <bundle-or-component> [--configure-mcp]
cncf skill uninstall <bundle-or-component>
```

The omitted target is the current component development directory. `cncf`
defaults to `project` scope. A source target must be normalized and admitted as
a component development directory; an explicit CAR uses the common archive
path. `user` scope requires an explicit choice because a development bundle
can influence unrelated projects.

## Scope

- locate a source or generated development skill bundle according to the CNCF
  manifest contract and record source-tree digest/freshness;
- validate the same manifest, compatibility, digest, name, and MCP-declaration
  rules as Textus Launcher before activation;
- install through the common staged scope model with development provenance;
- diagnose stale generated output, untracked source changes, source/package
  divergence, unsupported runtime/Codex compatibility, and configuration
  merge conflicts; and
- preserve source-aware component/dependency activation as an execution
  concern, separate from skill installation.

## Non-goals

- publishing a CAR, changing source files, or running Cozy automatically;
- making a development directory globally active by default;
- giving a bundle arbitrary filesystem, process, network, MCP-command, or
  external-AI authority; and
- replacing Textus Launcher's published-artifact installation path.

## Dependencies

- CNCF `SkillBundleManifest` contract and archive/source-location rules;
- Cozy source validation and CAR package projection; and
- shared launcher installation semantics, implemented independently where the
  two launchers intentionally remain separately distributed.

## Acceptance

- An admitted working tree installs its declared bundle into project scope and
  records source digest/freshness.
- The same packaged CAR produces an equivalent installed skill set through
  Textus Launcher for identical manifest content.
- Stale, divergent, invalid, colliding, or unsafe configuration inputs fail
  without changing the existing Codex scope.
- Focused specifications cover source resolution, project/user scope,
  source/package equivalence, and explicit MCP configuration merge.

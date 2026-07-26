# CNCF Launcher Development Strategy

Status: active

## Purpose

`cncf` is the component-development launcher. It must let a developer try the
same Codex skills that a released CAR later distributes, directly from the
working tree and without a publish step.

## Direction

CNCF Launcher consumes the same CNCF `SkillBundleManifest` as Textus Launcher,
but resolves a component development directory, generated development output,
or an explicit local artifact. It is the development counterpart to the
published artifact installation path; it does not define skill syntax or CAR
packaging.

## Roadmap

### Phase 1: Development Skill Bundle Installation

Status: planned.

Add `cncf skill list|install|status|update|uninstall`. Default installation
uses the current component development directory and project scope so an
unreleased skill does not unexpectedly affect unrelated Codex tasks. A user
scope remains explicit for deliberate cross-project testing. The phase shares
validation and installed-state semantics with Textus Launcher while adding
working-tree freshness and source/output provenance.

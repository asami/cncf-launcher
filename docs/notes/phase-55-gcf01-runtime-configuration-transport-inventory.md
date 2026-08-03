# Phase 55 GCF-01 CNCF Launcher Configuration Transport Inventory

Date: 2026-08-02

Status: frozen implementation inventory

The CNCF launcher is an external configuration transport boundary. Its current
launcher configuration, environment, command arguments, and runtime hand-off
may retain strings and compatibility spellings, but it must not select a
configuration binding, interpret a parameter semantic, or construct a second
effective-value authority.

Phase 55 assigns this repository four duties only: preserve original external
spelling for provenance, forward validated boundary input through the explicit
codec, reject malformed/non-canonical boundary encodings, and demonstrate that
transport does not change an admitted candidate's parameter, target, or value.

The canonical external namespace is `textus.*`. `textus.runtime.*`, `cncf.*`,
and `cncf.runtime.*` are decode-only compatibility aliases. A canonical spelling
and any alias for the same parameter/target in one source are a structural
configuration error; no precedence fallback selects one. Alias removal is
scheduled in GCF-09. The launcher neither extends that compatibility window nor
turns aliases into internal keys.

GCF-08 owns the executable codec and transport work; GCF-09 owns migration of
existing launcher consumers. This inventory deliberately makes no launcher
behavior change.

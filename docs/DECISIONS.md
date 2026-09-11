# Architecture decisions

## ADR-001 — Android first
Kotlin + Jetpack Compose.

## ADR-002 — No LLM runtime
Simulation and text generation are rule/template based. LLMs may help development but are not a game dependency.

## ADR-003 — Determinism
Custom stable RNG and seed-based generation are used instead of implicit randomness.

## ADR-004 — Optional adult module
The adult feature is isolated behind `core/adult-contracts`. It returns effect proposals; core decides whether to apply them.

## ADR-005 — Milestone-only manual CI
GitHub Actions workflows use `workflow_dispatch` only. During early development we do not build on every step, commit, PR, or module change. Actions are launched only at meaningful playable/visual milestones when there is something worth installing and testing on Android. Target checkpoints are: first living-map prototype, first civilization simulation prototype, first major integrated gameplay slice, and later release candidates. Between checkpoints, changes are validated by static review, deterministic/unit-test design and code inspection without consuming GitHub Actions minutes.

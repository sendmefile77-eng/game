# Architecture decisions

## ADR-001 — Android first
Kotlin + Jetpack Compose.

## ADR-002 — No LLM runtime
Simulation and text generation are rule/template based. LLMs may help development but are not a game dependency.

## ADR-003 — Determinism
Custom stable RNG and seed-based generation are used instead of implicit randomness.

## ADR-004 — Optional adult module
The adult feature is isolated behind `core/adult-contracts`. It returns effect proposals; core decides whether to apply them.

## ADR-005 — Manual CI
GitHub Actions workflows use `workflow_dispatch` only during early development to avoid wasting build minutes.

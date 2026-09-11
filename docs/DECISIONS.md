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

## ADR-006 — Preserve Grok adult-content intent during integration
Grok owns the adult/erotic content layer. ChatGPT reviews that module for architecture, contract compliance, determinism, bugs, integration quality, data safety and runtime stability, but does not reduce, soften, rewrite or remove Grok-authored adult content merely because of its erotic intensity. Content changes are made only when technically necessary for compatibility, when explicitly requested by the user, or when required by applicable platform/legal constraints. Architectural integration must preserve the intended adult-module behavior whenever possible.

## ADR-007 — Evolution, ancestry, hybrids and assimilation are separate systems
Biological evolution is simulated as population lineages and morphology, not as fixed fantasy-race labels. Environment, isolation, gene flow, mutation pressure and future biotechnology/radiation can cause divergence from the human baseline into morphs, subspecies and species. Structural body-plan changes require strong causes and do not occur as arbitrary visual randomization.

Biological ancestry is persistent and may contain multiple source lineages. Long-term contact and migration can produce stable hybrid lineages whose morphology is deterministically inherited from parent lineages. Continuous traits blend by ancestry; discrete body-plan traits use deterministic compatibility/inheritance rules so visuals remain coherent.

Cultural assimilation is explicitly separate from biological ancestry. Political conquest or cultural adoption may change identity, language, clothing and norms much faster than genes. A settlement changing owner never instantly changes the biology of its population. Both biological ancestry and cultural identity branch with the time-machine state.

Scene/portrait rendering must consume lineage, ancestry and morphology descriptors. It must never assume every character uses the baseline human rig; incompatible body plans require a compatible rig/asset recipe or a deterministic fallback.

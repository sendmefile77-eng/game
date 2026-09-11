# Chronosphere

Offline-first Android world-history sandbox. A deterministic procedural world develops settlements, civilizations, diplomacy, wars, dynasties, culture, economy, biological lineages and alternate historical branches without LLMs, paid APIs or cloud dependencies.

## Runtime principles

- no LLM or runtime-AI dependency;
- no paid/external API dependency;
- deterministic simulation by seed;
- local-first saves and history branches;
- Kotlin + Jetpack Compose on Android;
- optional modules must never bypass core validation;
- simulation must not depend on one specific portrait renderer or asset pack;
- GitHub Actions are manual milestone builds only.

## Current playable shell

The Android app is map-first. The world map is the primary navigation surface: tapping a settlement selects its civilization and highlights its territory. The lower panel is split into focused sections:

- **Держава** — civilization, economy, era, ruler, lineage and player interventions;
- **Персонаж** — notable-person card, ancestry/morphology, relationships and adult-only wardrobe state;
- **Час** — checkpoints, alternate-history forks, branch switching and local save/load;
- **Хроніка** — leading states, active wars and recent world events.

Long time advances run outside the Compose UI thread. The simulation remains deterministic and mutations are disabled while an advance is in progress.

## Modules

- `app` — Android UI shell and playable orchestration.
- `core/simulation` — deterministic clock, RNG and core events.
- `core/worldgen` — procedural planet generation, rivers and resources.
- `core/civilization` — settlements, states, borders, diplomacy, wars and alliances.
- `core/people` — significant people, rulers, dynasties, relationships and social profiles.
- `core/economy` — goods, production, shortages, trade and technology eras.
- `core/evolution` — population lineages, ancestry, admixture, hybrids and morphology.
- `core/society` — validated annual bridge between simulation context and optional modules.
- `core/scene` — deterministic renderer-neutral `SceneRecipe` / `ResolvedScene` contract.
- `core/history` — interventions, checkpoints, forks and branch comparison.
- `core/textgen` — rule/template-based chronicle text generation.
- `core/storage` — persistence contracts and history workspace snapshots.
- `core/adult-contracts` — stable integration boundary for the optional adult module.
- `feature/map` — Compose map renderer and civilization selection.
- `feature/adult` — optional implementation slot; core must run without it.

## Visual status

The rejected G-008 procedural raster art pack is **not** the final visual solution and is not a v0.1 release dependency. Character scenes always resolve locally to compatible asset layers or deterministic fallback so gameplay never depends on unfinished art.

A future high-quality portrait renderer is intentionally replaceable. It must first pass a standalone real-asset prototype gate proving identity continuity, offline operation, deterministic rendering, dressed/undressed consistency and morphology-safe body-plan handling before integration.

See `docs/DECISIONS.md`, `docs/ROADMAP.md`, `docs/PLAYABLE_V01_QA.md` and scene-contract documentation before changing shared architecture.

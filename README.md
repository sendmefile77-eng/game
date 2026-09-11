# Chronosphere

Offline-first Android sandbox where a procedurally generated world develops its own history: climate, settlements, civilizations, borders, conflicts, culture and later space expansion.

Runtime goals:
- no LLM dependency;
- no paid API dependency;
- deterministic simulation by seed;
- local saves;
- Android first;
- optional modules must never be able to bypass core validation.

## Modules

- `app` — Android UI shell.
- `core/simulation` — deterministic clock, RNG and core events.
- `core/worldgen` — procedural planet generation.
- `core/civilization` — civilization domain model and simulation hooks.
- `core/history` — historical event log.
- `core/textgen` — template-based text generation, no LLM.
- `core/storage` — persistence contracts.
- `core/adult-contracts` — stable integration boundary for the optional adult module.
- `feature/map` — Compose map renderer.
- `feature/adult` — optional implementation slot; core must run without it.

See `docs/` before changing shared architecture.

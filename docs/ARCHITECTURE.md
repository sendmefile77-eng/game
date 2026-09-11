# Architecture

## Direction
Chronosphere is an offline-first deterministic simulation. The simulation creates facts; UI and text layers only render those facts.

Data flow:
`seed -> worldgen -> simulation state -> events -> validated effects -> history -> renderer/text`

## Hard boundaries
- `core/simulation` owns deterministic time and random generation.
- `core/worldgen` owns generated physical world data.
- `core/adult-contracts` contains only stable DTOs/interfaces.
- `feature/adult` cannot directly mutate world state.
- Core validates every proposed effect before it is applied.
- Removing `feature/adult` must not stop the base game from compiling or running.
- No runtime LLM or paid API dependency.

## Determinism
For equal app version + simulation rules + seed + ordered input events, the world must produce the same result. Do not use system time, unseeded randomness or hash iteration order inside simulation logic.

# Architecture

## Direction
Chronosphere is an offline-first deterministic simulation. The simulation creates facts; UI, text and visual layers only render those facts.

Data flow:
`seed -> worldgen -> civilization -> economy/eras -> people/culture -> events -> validated effects -> history -> renderer/text/scene composer`

## Hard boundaries
- `core/simulation` owns deterministic time and random generation.
- `core/worldgen` owns generated physical world data and deposits.
- `core/civilization` owns aggregate civilization, diplomacy, war, settlements and territorial state.
- `core/economy` owns abstract goods, production, demand, stockpiles, shortages, trade routes, economic pressure and technology-era resolution. It returns validated civilization-level consequences instead of owning diplomacy or people.
- `core/people` owns notable people, rulers, dynasties, relationships and social profiles; it must remain bounded and must not simulate every inhabitant as a full entity.
- `core/history` branches every future-relevant simulation layer together: world, people and economy.
- `core/adult-contracts` contains only stable DTOs/interfaces.
- `feature/adult` cannot directly mutate world state.
- Core validates every proposed effect before it is applied.
- Removing `feature/adult` must not stop the base game from compiling or running.
- No runtime LLM or paid API dependency.

## Economy and eras
Economy is deliberately aggregate rather than per-citizen. Settlements and nearby resource deposits create annual production. Civilizations consume abstract goods, retain strategic stockpiles, develop shortages or surpluses, and trade only through diplomatically valid routes. War blocks bilateral trade. Trade and output affect treasury, stability and long-term technology growth.

Technology eras are derived from technology plus material prerequisites such as population, metal and fuel availability. Era transitions are simulation facts and therefore appear in the chronicle and branch with alternate history.

## Visual architecture
Chronosphere does not require one bespoke illustration per event. A generic deterministic offline scene composer resolves compatible layered recipes from simulation facts and logical asset keys. Asset packs provide style-specific local assets and compatibility metadata. Broken combinations must fall back to a simpler valid representation instead of rendering mismatched layers.

See `docs/SCENE_COMPOSER.md`.

## Determinism
For equal app version + simulation rules + seed + ordered input events, the world must produce the same result. Do not use system time, unseeded randomness or hash iteration order inside simulation logic. Scene composition follows the same rule: equal event + stable token + pack version must resolve to the same scene recipe.

## Time branches
Any state that materially affects future simulation must branch with history. World state, people/ruler state and economy state cannot be kept as a single global object while the user switches alternate timelines.

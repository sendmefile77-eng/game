# Roadmap

## Stage 0 — Foundation
- [x] Repository initialized.
- [x] Multi-module Gradle structure.
- [x] Deterministic RNG and simulation clock.
- [x] Deterministic procedural world generator.
- [x] First Compose map renderer.
- [x] Adult module contract boundary and no-op fallback.
- [x] Manual Android debug build workflow.
- [x] Bootstrap merged to `main`.
- [ ] First milestone build verification is intentionally deferred until v0.1 Playable gate.

## Stage 1 — Living planet
- [x] Rivers and resource deposits.
- [x] Settlements and initial civilizations.
- [x] Population, food, wealth, stability and technology simulation.
- [x] Colony founding and migration.
- [x] Chronicle/event feed.
- [x] Local save/load v1.

## Stage 2 — Politics and history
- [x] Territorial influence and borders.
- [x] Pairwise diplomacy.
- [x] Wars and casualties.
- [x] City capture and territorial transfer.
- [x] Peace treaties with war outcome.
- [x] Alliances and alliance dissolution.
- [x] Observer summaries for wars and alliances.

## Stage 3 — Intervention and time branches
- [x] Controlled player interventions.
- [x] Historical checkpoints.
- [x] Alternate-history branches with independent state.
- [x] Branch switching and restore.
- [x] Persistent branch/checkpoint save format.
- [x] Chronicle persistence in saves with legacy compatibility.
- [x] Branch divergence metrics.
- [x] Ukrainian observer/control UI with map-first mobile layout.

## Stage 4 — People, rulers and culture
- [x] Deterministic significant-person layer.
- [x] Rulers, succession and dynasties.
- [x] Family and relationship graph.
- [x] Evolving cultural/social profiles.
- [x] People state branches and restores with alternate history.
- [x] Observer summaries for rulers, dynasties and culture.
- [x] Validated runtime integration of `adult-contracts` effects without making core depend on `feature/adult`.
- [x] Playable notable-person cards with adult-only dressed/undressed visual state.
- [ ] Full civilization/ruler/person dossier screens after v0.1.

## Stage 5 — Economy and eras
- [x] Aggregate goods and production from population/resources.
- [x] Demand, strategic stockpiles and shortages.
- [x] Deterministic trade routes and regional specialization.
- [x] War blocks bilateral trade; alliances improve capacity.
- [x] Treasury/stability/technology consequences.
- [x] Technology-era progression through material prerequisites.
- [x] Economy state branches with alternate history and persists in history saves.
- [x] Economy/era observer summary and chronicle events.
- [ ] Institutions and taxation policies controlled by simulation/player choices.
- [ ] Infrastructure growth and urban transformation visible on the map.

## Stage 5E — Evolution and population lineages
- [x] Population lineages and morphology profiles.
- [x] Divergent morph/subspecies/species progression.
- [x] Biological ancestry separated from cultural assimilation.
- [x] Deterministic admixture and hybrid lineages.
- [x] Hybrid morphology derived from parent lineages.
- [x] Evolution state branches/restores with alternate history.
- [x] Evolution persistence in history saves.
- [x] Morphology/ancestry context reaches adult visual recipes.

## Stage 6 — Society and scene composition
- [x] Annual society bridge from living simulation into optional adult module.
- [x] Bounded validated adult effects applied back to reputation, relationships, demography and culture.
- [x] Era/economy/war/culture context integrated into adult selection.
- [x] Shared deterministic `core:scene` composer.
- [x] Morphology-aware dressed/undressed character-card recipes.
- [x] Optional G-007 adult-scene bridge to `core:scene`.
- [x] Android offline scene renderer with deterministic fallback.
- [x] APK/classpath scene-pack loading contract.
- [x] G-008 procedural raster pack evaluated and rejected as final visual layer; it is not a release dependency.
- [ ] High-quality portrait renderer remains a replaceable post-v0.1 presentation layer and must be proven by a real visual prototype before integration.

## v0.1 Playable milestone
- [x] Map-first observer sandbox.
- [x] Time advancement and interventions.
- [x] Politics, wars, alliances and territorial change.
- [x] People/dynasties/culture.
- [x] Economy and eras.
- [x] Alternate-history branches and checkpoints.
- [x] Evolution, ancestry and hybrids.
- [x] Character scene cards and adult-only undress action.
- [x] Long-run deterministic integration test added.
- [x] History save consistency validation hardened.
- [x] Full JVM regression suite wired into manual milestone workflow.
- [x] Rejected G-008 removed from the milestone gate; deterministic local fallback remains mandatory.
- [x] Map settlement selection connected to the playable Android shell.
- [x] Long time advances moved off the Compose UI thread.
- [x] Map-first UI split into focused state/person/time/chronicle panels.
- [ ] Run the first manual GitHub Actions milestone build.
- [ ] Install APK and complete `docs/PLAYABLE_V01_QA.md` phone pass.

## Stage 7 — Industrial and global civilization (post-v0.1)
- industrialization and mass logistics;
- institutions, taxation and infrastructure;
- global diplomacy/blocs;
- large wars and systemic crises;
- modern infrastructure and advanced technologies;
- transition conditions toward off-world expansion.

## Stage 8 — Space (post-v0.1)
- star systems and procedural planets;
- orbital infrastructure;
- interplanetary expansion;
- space polities, trade and conflict;
- long-term galactic simulation.

## Presentation strategy after v0.1
The simulation must never depend on a particular portrait engine. The current deterministic local renderer is a safe fallback, not the final art target. Any future 2D, 3D-to-pixel or other renderer consumes `ResolvedScene`/morphology descriptors and may be replaced without changing simulation state, saves or time branches.

A final character renderer is accepted only after a standalone real-asset prototype proves identity continuity, dressed/undressed consistency, offline operation and acceptable visual quality. Concept images do not satisfy this gate.

## Build policy
GitHub Actions stay manual-only. Run a build at meaningful playable/visual milestones, not after each commit, PR or internal module change.

The next intended Actions run is the **first v0.1 Playable milestone build**, after the playable-completion branch is reviewed and merged. No rejected visual pack is required for that build.

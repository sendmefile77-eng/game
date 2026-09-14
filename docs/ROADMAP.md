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
- [x] Institutions and taxation policies controlled by simulation/player choices.
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

## Stage 6H — Historical causality and world memory
- [x] Conservative deterministic cause → consequence links for supported event chains.
- [x] Persistent historical legacies for war, territorial loss, scarcity, migration, dynastic change, technological transition and population change.
- [x] Causal memory follows alternate-history branches through `HistoricalMemoryState`.
- [x] Save/load persists causal links and legacies while remaining compatible with older V1 memory snapshots.
- [x] Existing Chronicle “ПРИЧИНИ” card reads the active branch's persistent historical memory.
- [x] Regression tests cover duplicate suppression, war escalation, shortage-driven migration and save round-trips.
- [ ] Expand causal rules only when the simulation provides a defensible factual relationship; never infer arbitrary story causation from temporal proximity alone.

## Stage 6P — Internal politics and state cohesion
- [x] Deterministic tax regimes affect treasury, compliance and political pressure.
- [x] Landholder, merchant, military and bureaucratic elites have independent influence and loyalty.
- [x] Every settlement has a persistent provincial loyalty, unrest, autonomy and tax-burden state.
- [x] War, scarcity, distance, taxation and elite disloyalty can produce provincial unrest and rebellion.
- [x] Mature severe rebellions can become real successor states with transferred settlement, population, treasury and diplomacy.
- [x] Tax, unrest, rebellion and secession events feed the historical causality / world-memory layer.
- [x] V1 saves persist internal politics through optional backward-compatible rows.
- [x] The playable briefing surfaces internal pressure, taxes and rebellion danger.
- [x] Regression tests cover politics reconciliation, batch independence, secession and save round-trips.
- [x] Council, administration, courts and military command evolve as persistent state institutions.
- [x] Institutions affect tax collection, elite loyalty, provincial cohesion, rebellion pressure and stability.
- [x] Players can raise/lower taxes one step or reform the weakest institution through the normal one-command-per-turn loop.
- [x] Player-selected tax policy receives a temporary priority window before autonomous policy adaptation resumes.
- [x] Institution reform is recorded as an institutional historical transition and persistent legacy.
- [x] V1 saves persist institutions and player tax-policy priority while older rows remain valid.

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
- advanced institutions, fiscal systems and infrastructure;
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

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
- [ ] First milestone build verification is intentionally deferred until the playable sandbox slice is merged.

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
- [ ] Full civilization/ruler/person dossier screens.
- [ ] Validated runtime integration of `adult-contracts` effects without making core depend on `feature/adult`.

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

## Stage 6 — Industrial and global civilization
- industrialization and mass logistics;
- global diplomacy/blocs;
- large wars and systemic crises;
- modern infrastructure and advanced technologies;
- transition conditions toward off-world expansion.

## Stage 7 — Space
- star systems and procedural planets;
- orbital infrastructure;
- interplanetary expansion;
- space polities, trade and conflict;
- long-term galactic simulation.

## Build policy
GitHub Actions stay manual-only. Run a build at meaningful playable/visual milestones, not after each commit, PR or internal module change.

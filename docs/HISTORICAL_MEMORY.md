# Historical memory and long processes

Chronosphere keeps raw simulation facts authoritative. The historical-memory layer does not invent events and does not replace the simulation; it turns already-established state and `SimulationEvent`s into durable causal context that can survive long time jumps.

## What is stored per history branch

`HistoricalMemoryState` branches, checkpoints, saves and restores together with world / people / economy / evolution state.

It contains:

- **civilization foundations** — up to three current structural traits derived from settlement layout, production, exchange and authority;
- **historical processes** — multi-event arcs such as settlement expansion, shortages, wars, diplomatic realignment, technological transitions, dynastic transitions and population divergence;
- **open consequences** — limited long-lived aftermath that closes only when simulation conditions support it;
- **commitments** — durable player-selected structural policies, including superseded policies for historical provenance.

Raw events remain canon. The memory layer may group or age them, but it may not create a war, technology, mutation, institution or relationship that is absent from simulation state/events.

## Structural chronicle choices

A subset of chronicle choices now becomes a durable policy. Policies are encoded as `history_policy:*` tags on `Civilization.cultureTags`, so they automatically follow the existing `GameSnapshotV1`, checkpoints and alternate-history branches without a parallel mutable store.

Only known structural choices are promoted. Tactical emergency/war actions remain one-shot interventions.

A policy family has at most one active member. Selecting a later choice from the same family supersedes the previous policy. The old commitment remains in historical memory as `SUPERSEDED` rather than disappearing.

`HistoricalCommitmentEngine.applyRecurring` applies deliberately small deterministic benefit/cost deltas during each simulation slice. This makes the choice visible in later centuries without allowing one button to overpower the base simulation.

## Save compatibility

The outer history file remains `CHRONOSPHERE_HISTORY_V1`. Branch/checkpoint rows gained one optional trailing packed field for `HistoricalMemorySnapshotV1`. Old rows decode with `historicalMemory = null`; the next `HistoryTimeline.syncActive` reconstructs a conservative memory from the current state and still-visible recent events.

## Adult-module boundary

No adult contracts or `feature/adult` implementation are changed here. Historical policies use a namespaced culture-tag prefix. Adult modules may ignore unknown tags. If Grok later wants erotic/social consequences of a historical policy, it should consume the stable policy/history context through the existing validated integration boundary rather than mutating historical memory directly.

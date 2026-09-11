# Integration status

| Area | Owner | Status |
|---|---|---|
| Android shell | ChatGPT | Observer UI with time/save/load and political summaries |
| Simulation RNG/clock | ChatGPT | Deterministic foundation |
| Worldgen | ChatGPT | Terrain + hydrology + resources |
| Map renderer | ChatGPT | Terrain + rivers + territories + borders + settlements |
| Civilization simulation | ChatGPT | Population/economy/colonies/migration |
| Diplomacy and conflict | ChatGPT | Relations + geography-aware wars + war score + captures + peace + alliances |
| Adult contracts | ChatGPT | v1 frozen for Grok G-001 |
| Adult implementation | Grok | G-001 in progress on isolated branch |
| Save system | ChatGPT | Snapshot v1 persists diplomacy, wars, war outcomes and alliances |
| History/text | ChatGPT | Template chronicle for growth/migration/war/capture/peace/alliance |

`main` remains the integration branch. Optional modules are never allowed to become a hard runtime dependency of simulation core. GitHub Actions are milestone-only and manually triggered.

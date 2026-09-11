# Integration status

| Area | Owner | Status |
|---|---|---|
| Android shell | ChatGPT | Observer UI with time/save/load |
| Simulation RNG/clock | ChatGPT | Deterministic foundation |
| Worldgen | ChatGPT | Terrain + hydrology + resources |
| Map renderer | ChatGPT | Terrain + rivers + territories + borders + settlements |
| Civilization simulation | ChatGPT | Population/economy/colonies/migration |
| Diplomacy and conflict | ChatGPT | Relations + war lifecycle + casualties |
| Adult contracts | ChatGPT | v1 frozen for Grok G-001 |
| Adult implementation | Grok | G-001 READY in docs/GROK_TASK.md |
| Save system | ChatGPT | Snapshot v1 persists diplomacy and wars |
| History/text | ChatGPT | Template chronicle for growth/migration/war |

`main` remains the integration branch. Optional modules are never allowed to become a hard runtime dependency of simulation core. GitHub Actions are milestone-only and manually triggered.

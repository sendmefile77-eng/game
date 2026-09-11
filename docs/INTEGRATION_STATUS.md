# Integration status

| Area | Owner | Status |
|---|---|---|
| Android shell | ChatGPT | Living-planet UI in Stage 1 branch |
| Simulation RNG/clock | ChatGPT | Deterministic foundation |
| Worldgen | ChatGPT | Terrain + hydrology + resources |
| Map renderer | ChatGPT | Terrain + rivers + settlement overlays |
| Civilization simulation | ChatGPT | Population/economy/growth/colonies |
| Adult contracts | ChatGPT | v1 frozen for first Grok implementation |
| Adult implementation | Grok | Ready for isolated G-001 task |
| Save system | ChatGPT | Snapshot v1 + local Android Save/Load |
| History/text | ChatGPT | Event stream + template chronicle prototype |

`main` remains the integration branch. Optional modules are never allowed to become a hard runtime dependency of simulation core. GitHub Actions are milestone-only and manually triggered.

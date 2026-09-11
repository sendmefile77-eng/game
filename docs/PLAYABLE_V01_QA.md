# Chronosphere v0.1 Playable — milestone QA

This checklist is for the first Android APK that is worth installing and playing. GitHub Actions remain manual-only and must be triggered only after the current playable-completion branch is reviewed and merged.

The rejected G-008 procedural raster pack is **not** a release dependency. v0.1 may ship with the deterministic local fallback as long as the scene contract, identity/wardrobe semantics and offline behavior are correct. A higher-quality portrait renderer is a later replaceable presentation layer and needs its own real-asset prototype before integration.

## Automated gate

The manual `Android Debug` workflow must pass, in order:

1. JVM tests for simulation, worldgen, civilization, people, economy, evolution, society, history, storage, scene and `feature/adult`;
2. the long-run deterministic playable smoke test in `core:storage`;
3. `:app:assembleDebug`;
4. APK artifact upload.

A failed test blocks the APK milestone. Do not bypass the regression step just to obtain an artifact.

## Fresh world smoke test

- Launch without a previous save.
- Seed `424242` creates a world without a crash.
- Map, rivers, territories and settlements are visible.
- Tapping a settlement selects its civilization and highlights that territory/settlement.
- Population, cities, wars, alliances, trade and branch summary render.
- `+1 рік`, `+10 років`, `+100 років` advance time and preserve a valid world state.
- Long advances show progress and do not freeze the Compose UI thread.
- Switching the selected civilization never leaves a stale/dead selected person.
- State, character, time-machine and chronicle panels remain reachable in portrait mode.
- Character card renders a local scene or deterministic fallback instead of a blank area.
- Adult characters expose `Роздягнути`; minors never do.
- Dressed/undressed switching does not change identity, morphology or timeline state.

## Simulation integrity

Test at least these seeds: `424242`, `1`, `-1`, `987654321`.

For each seed:

- advance at least 100 simulated years;
- total population remains non-negative and the app remains responsive;
- every civilization has finite technology/stability/treasury values;
- no settlement has negative population or invalid ownership;
- wars can start/end without orphan references;
- alliances do not reference missing civilizations;
- economy values remain finite and stockpiles non-negative;
- evolution populations retain normalized biological ancestry;
- hybrid lineages, if created, keep both parent ancestry/morphology semantics;
- cultural assimilation does not overwrite biological ancestry;
- recent-event feed stays bounded and readable.

## Save/load and time branches

- Save a fresh world.
- Advance time and change the world.
- Load and confirm world, people, economy and evolution all return to the same tick.
- Create a checkpoint, advance, restore it, and confirm all persistent layers rewind together.
- Fork a branch, change it, switch back, and verify the original branch did not inherit future state.
- Save with multiple branches, restart the app, load, and repeat branch switching.
- Corrupt or truncate a history file: app must report load failure instead of silently mixing states.
- Legacy world-only save still loads by reconstructing missing people/economy/evolution layers.

## Adult/society integration

- Optional adult module is loaded in the full APK.
- Same seed/context produces the same adult event selection and same `ResolvedScene`.
- Adult event effects are bounded and only mutate core through validated proposals.
- Era, wealth, scarcity, urbanization, trade and war context affect eligibility/weights through the existing v1 context.
- Morphology/hybrid context reaches optional visual recipes without changing the core contract.
- No adult request or undressed scene is created for a participant under 18.
- Missing/incompatible visual asset uses the same-wardrobe morphology-safe fallback.

## Renderer-agnostic visual gate

Required for v0.1 regardless of whether a raster pack is installed:

- every character card resolves to either local asset layers or deterministic fallback;
- no network URL, runtime AI or external generator is used;
- the same person keeps the same identity/morphology inputs across dressed/undressed state;
- morphology-specific cards do not silently fall back to an incompatible baseline-human rig;
- missing scene-pack files never crash the app or leave the card blank;
- local asset packs continue to load from APK assets/classpath through `SceneAssetRepository` when present;
- no rejected G-008 PNG is required for launch, save/load, character navigation or time advancement.

A future production portrait renderer must pass a separate prototype gate using a real 3D/2D asset pipeline. Concept art or AI mockups are not accepted as evidence of runtime quality.

## Phone/manual UI pass

Test on at least one real Android phone in portrait mode:

- no controls are cut off by system bars;
- map keeps useful height on a typical phone;
- lower panel scrolls independently;
- state/person/time/chronicle panel buttons remain tappable at default font scaling;
- tapping map settlements changes the selected civilization without accidental rapid switching;
- controls that mutate simulation are disabled while a long advance is running;
- character scene does not stretch or overflow its card;
- long names wrap without pushing controls off-screen;
- repeated time advancement does not cause obvious memory growth or UI lockups;
- rotate/background/restore does not immediately crash the activity (full state restoration can be improved after v0.1).

## Milestone decision

Only after the automated gate passes and the critical manual checks above succeed should the artifact be called **v0.1 Playable**.

Post-v0.1 work can then expand dossiers, institutions, infrastructure, industrialization, global systems, additional renderer/content packs and later space simulation without blocking the first playable milestone.

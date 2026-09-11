# GROK TASK

**Status: ARCHIVED / REJECTED AS FINAL VISUAL PATH**

## G-008 — First offline adult visual asset pack

The experimental branch `grok/g-008-offline-visual-asset-pack` was evaluated and is **not** accepted as the final Chronosphere visual layer. The procedural raster result did not meet the required character quality and must not be merged merely to satisfy a milestone checklist.

### What remains valid
The architecture work completed before G-008 remains valid:

- `core:scene` owns the deterministic, content-neutral scene contract;
- `SceneRecipe` / `ResolvedScene` remain the renderer boundary;
- `feature/adult` stays optional and must not become a dependency of simulation core;
- APK/classpath local scene-pack loading remains supported;
- missing or incompatible assets must use deterministic morphology-safe fallback;
- no visual path may require network access, cloud generation, runtime AI or external APIs;
- adult visual requests remain strictly 18+.

### What is explicitly rejected
Do not treat the G-008 PNG/art-pack output as production character art. In particular:

- do not merge the old G-008 branch into `main` as the final renderer;
- do not make v0.1 Playable depend on G-008 raster coverage;
- do not reintroduce primitive procedural bodies, capsule limbs, ellipse-built figures or placeholder pixel characters as a claimed final solution;
- do not change simulation, saves or morphology contracts to fit an inadequate art pack.

### Current presentation policy
v0.1 may use the built-in deterministic local fallback when no compatible visual asset is available. That fallback is a resilience mechanism, not the final art target.

Any future production portrait system must first be proven outside the main game with a **real runtime prototype**, not a concept image. The prototype must demonstrate:

1. one adult character with acceptable face and anatomy;
2. stable identity across pose/age/wardrobe changes;
3. DRESSED and UNDRESSED states based on the same identity/body rig or equivalent canonical body representation;
4. fully local/offline operation;
5. deterministic input-to-output behavior;
6. morphology-safe handling of non-baseline body plans;
7. a visual result that is genuinely acceptable before integration work starts.

Possible future implementations may use 2D, 3D-to-pixel or another fully local technique. The simulation must remain independent from that choice.

### Integration rule for future visual work
A future visual branch may only be proposed for merge after the standalone prototype has been visually approved. Until then, work should focus on gameplay, simulation stability, UI, persistence and renderer-independent contracts.

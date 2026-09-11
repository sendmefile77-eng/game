# GROK TASK

**Status: READY**

## G-007 — Adult visual recipes → shared core:scene bridge

Base: current `main` after merged G-006 and Stage 6 `core:scene`.
Branch: `grok/g-007-core-scene-adapter`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
You may update `feature/adult/build.gradle.kts` only to depend on `:core:scene` and for tests.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.
Do not change `core/adult-contracts` v1.

### Goal
Connect the accepted G-006 morphology-aware adult visual system to the shared deterministic `core:scene` models/resolver without reducing, sanitizing or deleting the accepted adult catalogue/intensity.

The adult module must remain the owner of adult recipe compatibility/content. `core:scene` is the generic rendering/composition surface.

### Required bridge surface
Add a small public, no-argument bridge/provider in `feature/adult` that ChatGPT can load from the app without a compile-time dependency on `feature:adult` implementation. It may expose public methods returning `core:scene.ResolvedScene` for:
- a character card in dressed state;
- the adult-only `undressed` character-card state;
- an already-selected/evaluated adult event visual where practical.

Keep the public surface minimal and deterministic. Document exact class/method names in your completion report.

### Mapping rules
- preserve G-006 morphology/rig eligibility before resolving a shared scene;
- translate the selected adult visual recipe into `core:scene` keys without inventing a different scene;
- preserve stable logical recipe id, pack/version, rig, pose, wardrobe, setting/background, camera, lighting and effect/layer keys;
- dressed card maps to a portrait/card-compatible shared scene;
- undressed card maps to `SceneIntent.CHARACTER_UNDRESS` + `WardrobeState.UNDRESSED`;
- under-18 participants must never produce the undressed path;
- morphology/hybrid continuity from G-006 must be retained;
- if the adult recipe layer chooses a morphology-safe fallback, the shared scene result must also remain a fallback and must not silently switch wardrobe state;
- same request/context must produce the same `ResolvedScene` and `sceneKey`;
- missing morphology remains backward-compatible with baseline G-006 behavior;
- do not bypass the G-006 compatibility filter just to satisfy `core:scene`;
- no network, filesystem, cloud, LLM, system time or uncontrolled randomness.

### Important architecture note
Contract v1 still has scene-level morphology only. Do not invent participant-specific morphology or edit contracts. Report any exact v2 request separately.

### Tests
Add tests for at least:
- dressed adult card resolves through shared `core:scene` deterministically;
- undressed adult card resolves with `WardrobeState.UNDRESSED` deterministically;
- hybrid/divergent morphology keeps a compatible rig/fallback through the bridge;
- under-18 undress remains impossible;
- adult recipe id/logical asset continuity survives translation;
- fallback status and wardrobe state survive translation;
- G-004/G-005/G-006 regression tests remain unchanged/passing by design.

### Completion
Commit to `grok/g-007-core-scene-adapter` and open a PR to `main`. Do not merge it. Do not run GitHub Actions.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. public bridge class/method names;
7. mapping from adult recipe → `core:scene`;
8. dressed/undressed behavior;
9. morphology/fallback behavior;
10. any requested contract-v2/shared-interface changes.

ChatGPT will review and integrate the result.

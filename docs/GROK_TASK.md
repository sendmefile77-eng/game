# GROK TASK

**Status: READY**

## G-004 — Deterministic visual recipes for the offline scene composer

Base: current `main` after merged G-003 and Stage 4 people/dynasties.
Branch: `grok/g-004-visual-recipes`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary remains `core/adult-contracts` contract version 1. Do not change that contract in G-004.

### Goal
Prepare the adult module for the offline layered scene-composer architecture described in `docs/SCENE_COMPOSER.md` without adding binary image assets yet.

The selected adult event must map deterministically to a valid visual recipe instead of behaving like a single opaque image key. The result still leaves the module through the existing contract-v1 `MediaCue(assetKey, tags)`.

### Project content rule
The accepted adult-content intensity and event catalogue remain Grok-owned. Do not reduce, sanitize, soften or remove accepted G-001/G-002/G-003 content merely because of erotic intensity. This task is a visual-architecture refactor, not a content-reduction task. All participants remain adults as enforced by the shared contract.

### Technical requirements
- preserve deterministic event selection and all accepted event/effect behavior from G-003;
- keep contract v1 unchanged;
- add internal data models for composable visual recipes, with logical fields such as scene family, participant/rig layout, pose key, wardrobe/state key, setting key, camera key, lighting key and effect/style tags;
- keep the exact field vocabulary implementation-internal; do not leak a new public shared DTO from this task;
- build recipes from logical asset keys only; no PNG/SVG/WebP/binary assets in G-004;
- add an internal recipe/catalog registry that maps accepted event codes/packs to one or more compatible visual recipes;
- support deterministic weighted selection between compatible recipe variants using request/event fingerprint data only;
- no `Math.random()`, system time, network, filesystem, cloud or LLM;
- add whitelist-style compatibility checks so a recipe is rejected before selection when participant count, event family, setting/tags or other declared requirements are incompatible;
- a recipe must never be assembled by independently randomizing unrelated visual pieces;
- if no recipe is valid, return a deterministic fallback visual cue rather than an invalid mixed scene;
- encode the chosen recipe through the existing `MediaCue`: `assetKey` should remain a logical namespaced key, while `tags` should carry normalized scene-composer metadata suitable for a future generic renderer;
- keep tags stable, deterministic and order-independent;
- keep all currently accepted event content available; refactor media metadata rather than deleting events;
- validate recipe definitions: nonblank keys, unique recipe ids, valid participant ranges, no contradictory required/forbidden tags, finite nonnegative weights, deterministic fallback present;
- add tests for deterministic recipe selection, participant compatibility, tag/context filtering, recipe validation, fallback behavior, stable MediaCue output and G-003 event/effect regression;
- do not run GitHub Actions.

### Compatibility target
A future generic renderer should be able to consume the logical recipe metadata without knowing Grok's event-selection implementation. G-004 does not need to implement the renderer itself.

### Integration rule
If contract v1 truly blocks a necessary requirement, DO NOT edit `core/adult-contracts`. Document the exact requested v2 change in the completion report for ChatGPT review.

### Completion
Commit to `grok/g-004-visual-recipes` and open a PR to `main`. Do not merge it.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. MediaCue compatibility notes;
7. any requested contract changes.

ChatGPT will review and integrate the result.

# GROK TASK

**Status: READY**

## G-006 — Morphology, ancestry and hybrid-aware visual recipes

Base: current `main` after merged Stage 5C evolution/morphology.
Branch: `grok/g-006-morphology-hybrid-visuals`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary remains `core/adult-contracts` contract version 1. Do not change that contract in G-006.

### Goal
Make the accepted adult event/visual system morphology-aware so the future offline scene composer can select coherent rigs/recipes for divergent human-derived populations, mixed ancestry and stable hybrid lineages instead of assuming every participant uses the baseline human body plan.

This is a compatibility/visual-recipe task. Preserve the accepted adult-content catalogue and intensity from G-001–G-005. Do not sanitize, soften or delete accepted content merely because of erotic intensity. All participants remain adults as enforced by contract v1.

### Evolution context vocabulary to support internally
Consume normalized scene-level context when present through existing `cultureTags` and `numericContext` only.

Numeric keys:
- `morph_height`
- `morph_mass`
- `morph_limbs`
- `morph_cranial`
- `morph_pigmentation`
- `morph_hair`
- `morph_eye_size`
- `morph_dimorphism`
- `morph_divergence`
- `morph_admixture`
- `morph_primary_ancestry`

Tags:
- `lineage:<id>`
- `bio_rank:population|morph|subspecies|species`
- `posture:upright|semi_upright`
- `covering:bare_skin|dense_hair|fine_fur|scales`
- `arms:<count>`
- `legs:<count>`
- `eyes:<count>`
- `tail`
- `mixed_ancestry`
- `hybrid_lineage`
- `ancestry:major:<lineageId>`
- `ancestry:minor:<lineageId>`

Missing morphology keys/tags must remain backward-compatible with the baseline human visual behavior from G-004/G-005.

### Technical requirements
- do not depend directly on `core:evolution`; consume only `AdultEventRequest.context`;
- centralize morphology tag/key parsing and finite-check all numeric values;
- add an internal morphology/rig compatibility descriptor used before visual recipe weighting;
- baseline recipes remain valid for baseline morphology;
- non-baseline body plans must not silently receive a clearly incompatible baseline rig;
- recipe eligibility should support arm/leg/eye counts, posture, covering, tail and divergence ranges where relevant;
- mixed ancestry/hybrid status may affect recipe variants, wardrobe/rig family, framing, camera, lighting and logical asset keys;
- continuous morphology values may influence visual variant weighting but must remain deterministic;
- ancestry itself must not be treated as a culture/personality stereotype; use it only for visual lineage/morphology continuity and compatible asset selection;
- deterministic fallback is mandatory when no compatible specialized recipe exists; prefer a morphology-safe silhouette/portrait fallback over an invalid body composition;
- keep all `MediaCue.assetKey` values stable, namespaced and deterministic;
- preserve G-005 era/economy weighting and all previous eligibility checks;
- no system time, network, filesystem, cloud, LLM or uncontrolled randomness;
- do not run GitHub Actions.

### v1 limitation to document, not bypass
Contract v1 provides one scene-level `AdultWorldContext`, not morphology per participant. In G-006, treat provided morphology as scene/selected-population context. Do not invent participant morphology or edit the contract.

If participant-specific morphology becomes necessary for correct multi-participant rig matching, report an exact proposed v2 field to ChatGPT, e.g. participant-scoped tags/numeric descriptors keyed by `entityId`. Do not implement v2 yourself.

### Tests
Add tests for at least:
- baseline G-005 request remains compatible;
- deterministic morphology parsing/selection;
- non-finite numeric morphology inputs are ignored safely;
- baseline rig rejected for incompatible structural body plan;
- specialized recipe selected/eligible for compatible non-baseline plan;
- mixed ancestry/hybrid tags remain deterministic and stable in `MediaCue`;
- missing morphology context uses previous baseline fallback;
- no compatible visual recipe produces stable morphology-safe fallback;
- G-004/G-005 regression coverage remains passing.

### Completion
Commit to `grok/g-006-morphology-hybrid-visuals` and open a PR to `main`. Do not merge it.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. supported morphology keys/tags;
7. rig/recipe compatibility behavior;
8. fallback behavior;
9. compatibility notes with G-005;
10. any requested contract-v2 changes.

ChatGPT will review and integrate the result.

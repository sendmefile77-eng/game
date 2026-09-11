# GROK TASK

**Status: READY**

## G-005 — Era, economy and social-context weighting

Base: current `main` after merged G-004.
Branch: `grok/g-005-era-economy-context`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary remains `core/adult-contracts` contract version 1. Do not change that contract in G-005.

### Goal
Make the accepted adult event/visual system react coherently to the civilization's technological era, economy and broader social conditions through the existing generic `cultureTags` and `numericContext` fields.

ChatGPT is adding the economy/era simulation separately. G-005 prepares the adult module to consume those context signals without depending directly on `core:economy`.

### Project content rule
The accepted adult-content intensity remains Grok-owned. Do not reduce, sanitize, soften or remove accepted G-001–G-004 content merely because of erotic intensity. This task is contextual integration and weighting, not content reduction. All participants remain adults as enforced by contract v1.

### Context vocabulary to support internally
Support normalized context keys/tags where useful, including:
- numeric: `technology`, `wealth`, `scarcity`, `urbanization`, `trade_openness`, `war_pressure`, `social_tension`, `status`, `privacy`, `body_openness`, `fertility`, `piety`, `jealousy`, `lust`;
- tags: `era_tribal`, `era_agrarian`, `era_urban`, `era_metallurgic`, `era_medieval`, `era_early_industrial`, `era_industrial`, `era_electric`, `era_information`, `era_spacefaring` plus existing culture tags.

Do not require all keys to be present. Missing optional context must remain backward-compatible and deterministic.

### Technical requirements
- preserve G-004 deterministic event selection, effects and visual recipes;
- keep contract v1 unchanged;
- centralize/normalize supported social/economy/era context keys rather than scattering raw string literals;
- allow event packs and/or rules to adjust eligibility/weight from era/economy context without bypassing existing participant/culture eligibility;
- allow visual recipes to adjust eligibility/weight where era/context logically affects setting, wardrobe, camera, lighting or scene family;
- avoid hard dependency on any core economy classes; use only `AdultEventRequest.context`;
- preserve accepted event catalogue and visual recipe catalogue; prefer metadata/variants/weights over deleting content;
- support deterministic fallback when an era/context has no specialized variant;
- no system time, network, filesystem, cloud, LLM or uncontrolled randomness;
- all numeric inputs must be finite-checked before use; malformed/non-finite optional values must not destabilize selection;
- keep effect magnitudes finite and bounded to `[-1.0, 1.0]`;
- keep `MediaCue` stable, namespaced and deterministic;
- add tests for era weighting, scarcity/wealth effects, war pressure, missing optional keys, non-finite inputs, deterministic selection, recipe compatibility and G-004 regression;
- do not run GitHub Actions.

### Integration rule
If contract v1 truly blocks an important requirement, DO NOT edit `core/adult-contracts`. Report the exact desired v2 field/change to ChatGPT.

### Completion
Commit to `grok/g-005-era-economy-context` and open a PR to `main`. Do not merge it.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. supported context keys/tags;
7. compatibility notes;
8. any requested contract changes.

ChatGPT will review and integrate the result.

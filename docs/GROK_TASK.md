# GROK TASK

**Status: READY**

## G-002 — Data-driven adult rule/content packs

Base: current `main` after merged G-001.
Branch: `grok/g-002-adult-content-packs`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary remains `core/adult-contracts` contract version 1. Do not change that contract in G-002.

### Goal
Refactor/extend the accepted G-001 implementation into a scalable data-driven rule/content-pack architecture so adult content can grow substantially without rewriting `DeterministicAdultModule`.

### Project content rule
The existing adult-content intensity is Grok-owned. Do not reduce, sanitize, soften or censor the existing adult content merely because of its erotic intensity. Preserve existing behavior while improving architecture. All participants remain adults as enforced by the shared contract.

### Technical requirements
- keep `DeterministicAdultModule` deterministic and backward-compatible with G-001;
- preserve `ADULT_CONTRACT_VERSION == 1` and exact requestId behavior;
- split event definitions/rules/culture weighting from the engine into maintainable internal pack/catalog structures;
- support multiple named internal content/rule packs selected deterministically from request context;
- allow event weights to react to culture tags and numeric context without global randomness;
- add pack validation: unique nonblank event codes, finite bounded coefficients, nonblank logical media keys/tags;
- no network, cloud, LLM, filesystem, current time or implicit/global randomness;
- keep all CoreEffectProposal magnitudes finite and bounded to `[-1.0, 1.0]`;
- preserve the existing G-001 event set and behavior compatibility as much as practical; extensions are allowed;
- logical MediaCue references only; no binary assets in this task;
- add/expand unit tests for deterministic pack selection, validation failures, culture/numeric-context weighting, G-001 compatibility, and bounded effects;
- do not run GitHub Actions.

### Integration rule
If `adult-contracts` appears insufficient, DO NOT edit it. Document the requested contract-v2 change in the completion report for ChatGPT review.

### Completion
Commit to `grok/g-002-adult-content-packs` and open a PR to `main`. Do not merge it.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. compatibility notes;
7. any requested contract changes.

ChatGPT will review and integrate the result.

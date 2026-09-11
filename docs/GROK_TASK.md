# GROK TASK

**Status: READY**

## G-003 — Context eligibility and social-norm weighting

Base: current `main` after merged G-002.
Branch: `grok/g-003-context-eligibility`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary remains `core/adult-contracts` contract version 1. Do not change that contract in G-003.

### Goal
Make the accepted G-002 pack engine context-aware enough for the upcoming people/rulers/culture simulation. Event selection must remain deterministic but should reject logically incompatible rules before weighting them.

### Project content rule
The adult-content intensity remains Grok-owned. Do not reduce, sanitize, soften or remove accepted G-001/G-002 content merely because of erotic intensity. This task is about logic and contextual eligibility, not censorship. All participants remain adults as enforced by the shared contract.

### Technical requirements
- keep `DeterministicAdultModule` deterministic and compatible with contract v1;
- add internal eligibility metadata/rules for events without changing `adult-contracts`;
- support participant-count constraints (minimum/maximum participants) so pair/group/solo contexts cannot select structurally incompatible events;
- support required and forbidden culture tags;
- support optional numeric-context gates/ranges with finite validation;
- standardize support for upcoming social-context keys such as `privacy`, `body_openness`, `monogamy`, `jealousy`, `fertility`, `piety`, `status`, `tension` and `lust` where useful;
- keep weighting separate from eligibility: ineligible rules have zero chance, eligible rules continue through deterministic weighted selection;
- provide a deterministic safe fallback when a selected pack has no eligible event, without network/time/randomness and without changing the contract;
- validate eligibility definitions: sensible participant ranges, nonblank tags/keys, finite numeric thresholds, min <= max;
- preserve the accepted G-002 packs and their content intensity; adapt metadata rather than deleting existing rules;
- keep all effect magnitudes finite and bounded to `[-1.0, 1.0]`;
- no network, cloud, LLM, filesystem, current time or global randomness;
- logical `MediaCue` references only; no binary assets;
- add tests for participant-count filtering, required/forbidden tags, numeric gates, deterministic fallback, tag-order independence and G-002 regression behavior;
- do not run GitHub Actions.

### Integration rule
If contract v1 truly blocks an important requirement, DO NOT edit `core/adult-contracts`. Document the exact requested v2 field/change in the completion report and let ChatGPT decide.

### Completion
Commit to `grok/g-003-context-eligibility` and open a PR to `main`. Do not merge it.

Report:
1. branch;
2. commit SHA;
3. PR;
4. changed files;
5. tests;
6. compatibility notes;
7. any requested contract changes.

ChatGPT will review and integrate the result.

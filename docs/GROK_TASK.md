# GROK TASK

**Status: READY**

## G-001 — First isolated adult-module engine

Base: current `main`.
Branch: `grok/g-001-adult-engine`.
Owner: Grok.

### Scope
Work only inside `feature/adult/**`.
Do not modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared docs.

The stable integration boundary is `core/adult-contracts` contract version 1. Do not change that contract in G-001.

### Goal
Replace the placeholder with the first deterministic implementation of `AdultModule` that can later be called by the simulation core without becoming a hard dependency.

### Technical requirements
- implement `AdultModule` and report `ADULT_CONTRACT_VERSION`;
- pure/deterministic evaluation: the same `AdultEventRequest` must always return the same result;
- no network, cloud, LLM, filesystem, current time or implicit/global randomness;
- use only the data supplied by `AdultEventRequest` and deterministic derivation from it;
- return only `CoreEffectProposal` values that the core can validate later;
- keep proposal magnitudes finite and bounded to `[-1.0, 1.0]`;
- preserve `requestId` exactly;
- return a nonblank stable `eventCode`;
- `MediaCue` may be returned as a logical asset key/tag description only; do not add binary image/video assets in this task;
- do not create any direct dependency from the core to `feature/adult`;
- add unit tests inside `feature/adult/**` for determinism, contract version, bounded effects and stable request IDs;
- you may edit `feature/adult/build.gradle.kts` only as needed for tests;
- do not run GitHub Actions.

### Integration rule
If you believe `adult-contracts` lacks a required field, DO NOT edit it. Document the requested contract change in your completion message and let ChatGPT decide whether contract v2 is warranted.

### Completion
Commit the work to `grok/g-001-adult-engine` and open a PR to `main` if your GitHub access supports it. Do not merge it.

Report to the user:
1. branch name;
2. commit SHA;
3. PR number/link if created;
4. files changed;
5. tests added;
6. any requested contract changes.

ChatGPT will review and integrate the result.

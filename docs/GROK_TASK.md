# GROK TASK

**Status: READY**

## G-008 — First real offline adult visual asset pack

Base: current `main` after merged Stage 7 + Stage 7B (`core:scene` renderer, playable character cards, G-007 bridge, classpath resource loading).

Branch: `grok/g-008-offline-visual-asset-pack`

Owner: Grok.

### Scope
Work only inside `feature/adult/**`.

You may add:
- `feature/adult/src/main/resources/scene_packs/adult/**`;
- tests under `feature/adult/src/test/**` that validate resource presence/coverage;
- minimal `feature/adult` code only if strictly required to expose or validate the existing visual pack.

Do **not** modify `core/**`, `app/**`, `feature/map/**`, root Gradle files, workflows or shared contracts.
Do not change `core/adult-contracts` v1.
Do not run GitHub Actions.
Do not merge to `main`.

### Read first
Use the current:
- `docs/SCENE_ASSET_PACK.md`;
- `AdultCardRecipes.kt`;
- `BundledVisualRecipes.kt`;
- `AdultSceneBridge.kt`;
- `AdultSceneMapper.kt`.

The renderer is already implemented. This task is **asset/content production and mapping**, not another renderer rewrite.

### Goal
Provide the first real offline raster pack that makes the accepted G-006/G-007 adult scenes visible in the Android character cards and event scene pipeline.

Do not reduce, sanitize, rename or delete the already accepted adult catalogue. Map assets to the existing recipe ids and resolved logical keys.

### Packaging contract
Because `feature/adult` is a Kotlin/JVM module, all pack files must be Java resources under:

`feature/adult/src/main/resources/scene_packs/adult/`

Required manifest:

`feature/adult/src/main/resources/scene_packs/adult/manifest.tsv`

Header/format must be exactly `CHRONOSPHERE_SCENE_ASSET_V1` as documented in `docs/SCENE_ASSET_PACK.md`.

Target canvas: `1024 x 1536` portrait.
Use local PNG or WebP only. Transparent aligned layers are preferred; a full precomposed image mapped to `recipe:<recipeId>` is allowed for this first pack.
No URLs, network fetching, cloud calls, runtime generation, filesystem writes, system time or uncontrolled randomness.

### Required character-card coverage
The first pack must visibly cover every current `AdultCardRecipes` recipe id:

- `card.dressed.baseline`
- `card.undressed.baseline`
- `card.dressed.hybrid`
- `card.undressed.hybrid`
- `card.undressed.quad`
- `card.undressed.tailed`
- `card.undressed.scaled`
- `card.dressed.scaled`

Each must map to a real local raster layer or precomposed illustration through the manifest.

Dressed/undressed pairs must use one coherent visual style and compatible anatomy. Hybrid/divergent cards must visibly respect the morphology implied by the recipe instead of silently reverting to a baseline human body.

### Event-scene coverage
Also add a representative first set of real local event visuals from the **existing** `BundledVisualRecipes` catalogue.

Minimum:
- at least 12 distinct existing event recipe ids;
- coverage across the existing major adult content packs/families rather than 12 near-identical scenes from one family;
- include baseline and morphology-safe/fallback-capable coverage where the current recipe system can select it;
- use exact existing recipe ids and logical keys from the current code.

Do not invent a parallel event system just for assets.

### Visual consistency
This is one coherent first art pack, not a random collection.

Requirements:
- consistent art direction, body construction, perspective and lighting language;
- same canonical 1024x1536 alignment for composable layers;
- no accidental floating wardrobe/body parts;
- asset content must match the selected rig/wardrobe/setting intent;
- `UNDRESSED` assets must remain `UNDRESSED`; do not disguise them as dressed fallback;
- morphology-safe fallback must remain visually morphology-safe;
- no minors anywhere in the adult asset pack.

Preserve the adult content level already defined by Grok's accepted catalogue; this task does not ask for censorship or intensity changes.

### Manifest/resource tests
Add JVM tests that at minimum verify:
- `scene_packs/adult/manifest.tsv` is loadable through the classloader;
- every manifest path resolves through `ClassLoader.getResourceAsStream`;
- all eight current `AdultCardRecipes` ids have manifest coverage as `recipe:<id>` or an equivalent exact logical mapping used by G-007;
- at least 12 existing event recipe ids have raster coverage;
- no duplicate logical keys in the manifest;
- referenced files are non-empty;
- no manifest path is an HTTP/HTTPS URL;
- existing G-004/G-005/G-006/G-007 behavior is not changed by the asset files.

### Completion
Commit to `grok/g-008-offline-visual-asset-pack` and open a PR to `main`.
Do not merge it. Do not run GitHub Actions.

Report:
1. branch;
2. commit SHA;
3. PR;
4. manifest path;
5. number of raster files and total pack size;
6. all covered character-card recipe ids;
7. all covered event recipe ids;
8. whether assets are layered or precomposed;
9. tests added/results;
10. any exact renderer/contract limitation discovered, without modifying `core`/`app` to work around it.

ChatGPT will review and integrate the result.

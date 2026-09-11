# Offline scene asset packs

`core:scene.ResolvedScene` is deliberately renderer-agnostic. Android rendering uses local asset packs merged into the APK; there is no network fetch, runtime image generation or paid service.

## Layout contract

Every layered raster pack uses one canonical canvas per pack. The first playable target is portrait `1024 x 1536`. PNG or WebP layers should share the same canvas and transparent alignment so they can be drawn directly over one another.

Recommended layer order:

1. background (`0-99`)
2. body/rig (`100-199`)
3. wardrobe / body-state (`200-299`)
4. scene/effect overlays (`300-399`)
5. lighting / grading (`400-499`)

A pack may also provide a full precomposed illustration for `recipe:<recipeId>`. It is still addressed through the same deterministic scene and may be used as a temporary high-quality asset while a more modular rig is being built.

## Manifest

Known manifests are merged in order. The base app uses:

`scene_packs/base/manifest.tsv`

The optional adult module uses:

`scene_packs/adult/manifest.tsv`

Format `CHRONOSPHERE_SCENE_ASSET_V1`:

```text
CHRONOSPHERE_SCENE_ASSET_V1
PACK	pack-id	1	1024	1536
ASSET	bg.card.neutral	0	scene_packs/base/background/card_neutral.webp
ASSET	rig.human.card	100	scene_packs/base/rig/human_card.webp
ASSET	wardrobe:wardrobe.clothed	200	scene_packs/base/wardrobe/clothed.webp
ASSET	light.card.soft	400	scene_packs/base/light/soft.webp
ASSET	recipe:card.portrait.human	250	scene_packs/base/recipe/card_portrait_human.webp
```

Rules:

- fields are tab-separated;
- blank lines and `#` comments are ignored;
- logical keys and paths must be nonblank;
- `zIndex` must be an integer in `-1000..1000`;
- asset paths are APK asset paths, not URLs;
- later manifests override the same logical key;
- duplicate keys inside one manifest are invalid;
- renderer accepts PNG, WebP and JPEG through Android `BitmapFactory`;
- no asset may require network, filesystem writes, system time or uncontrolled randomness.

## ResolvedScene → asset keys

The Android renderer asks for keys in this order and then sorts resolved assets by `zIndex`:

- `backgroundKey`
- `bodyRigKey`
- `poseKey`
- `recipe:<recipeId>`
- every `layerKeys` entry
- `lightingKey`

Missing optional keys are allowed. If no drawable layer resolves, the app renders a deterministic built-in silhouette fallback instead of crashing.

## Compatibility

Asset content must match the `ResolvedScene` chosen by recipe/rig compatibility. An asset pack must never reinterpret `UNDRESSED` as `DRESSED`, replace a divergent rig with a baseline body, or change participants. The renderer only draws the already-resolved scene.

The optional adult pack remains owned by `feature/adult`; `app` reads its merged APK assets by logical path and never references that module's resource IDs.

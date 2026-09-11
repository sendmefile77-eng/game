# Offline scene asset packs

`core:scene.ResolvedScene` is deliberately renderer-agnostic. Android rendering uses only local packaged content; there is no network fetch, runtime image generation or paid service.

## Layout contract

Every layered raster pack uses one canonical canvas per pack. The first playable target is portrait `1024 x 1536`. PNG or WebP layers should share the same canvas and transparent alignment so they can be drawn directly over one another.

Recommended layer order:

1. background (`0-99`)
2. body/rig (`100-199`)
3. wardrobe / body-state (`200-299`)
4. scene/effect overlays (`300-399`)
5. lighting / grading (`400-499`)

A pack may also provide a full precomposed illustration for `recipe:<recipeId>`. It is still addressed through the same deterministic scene and may be used while a more modular rig is being built.

## Where files live

The base Android app stores its pack in Android assets:

`app/src/main/assets/scene_packs/base/...`

The optional adult implementation is a Kotlin/JVM module, so its pack must be Java classpath resources:

`feature/adult/src/main/resources/scene_packs/adult/...`

At runtime `SceneAssetRepository` checks Android `AssetManager` first and then the application `ClassLoader`. This keeps `app` independent of `feature/adult` resource IDs and lets the optional JVM JAR contribute images to the installed APK.

## Manifest

Known manifests are merged in order:

`scene_packs/base/manifest.tsv`

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
- paths are local packaged resource paths, never URLs;
- later manifests override the same logical key;
- duplicate keys inside one manifest are invalid;
- PNG, WebP and JPEG are decoded through Android `BitmapFactory`;
- no asset may require network, filesystem writes, system time or uncontrolled randomness.

## ResolvedScene → asset keys

The renderer requests these logical keys and then sorts resolved layers by `zIndex`:

- `backgroundKey`
- `bodyRigKey`
- `poseKey`
- `recipe:<recipeId>`
- every `layerKeys` entry
- `lightingKey`

Missing optional keys are allowed. If no drawable layer resolves, the app renders a deterministic built-in silhouette fallback instead of crashing.

## Compatibility

Asset content must match the `ResolvedScene` already chosen by recipe/rig compatibility. A pack must never reinterpret `UNDRESSED` as `DRESSED`, replace a divergent rig with a baseline body, or change participants. The renderer draws the resolved scene; it does not reinterpret it.

The optional adult pack remains owned by `feature/adult` and is loaded only as local classpath resources packaged with that optional implementation.

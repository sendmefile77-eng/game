# Offline Scene Composer

## Goal
Chronosphere uses deterministic layered scene composition instead of requiring a unique illustration for every event. The composer renders simulation facts; it never invents a different event.

Input concept:
`worldSeed + eventId + rngToken + participants + culture + location + outcome -> compatible scene recipe -> renderer`

The same inputs must always resolve to the same scene recipe for a given content-pack version.

## Scene recipe
A scene recipe is data, not a bitmap. It references compatible assets such as:
- character/body rig;
- pose/animation rig;
- clothing layers and clothing state;
- expression and appearance variants;
- location/background set;
- props;
- foreground/effect layers;
- lighting preset;
- camera/framing preset;
- optional pack-specific visual layers.

Actual binary art is supplied by asset packs. Core stores logical asset keys only.

## Character-card visual actions
Adult character cards may expose deterministic visual actions such as **`Роздягнути`**. These are scene-state transitions, not independent random image requests.

Rules:
- the action exists only for characters who are at least 18 at the current tick;
- the action changes the wardrobe state of the same character identity/morphology and preserves lineage, ancestry, body plan and visible appearance continuity;
- rig/body compatibility is checked before resolution;
- a non-baseline morphology must never receive a clearly incompatible baseline body rig;
- if the active asset pack has no compatible undressed representation, use a deterministic morphology-safe fallback instead of broken composition;
- dressed and undressed representations of the same character should share stable appearance/morphology keys so the person remains recognizably the same character;
- optional adult asset packs may provide richer wardrobe states through the same scene interface without changing core simulation facts.

## Compatibility first
Random mixing is forbidden. Every selectable combination must pass compatibility rules before weighting.

A compatibility rule may constrain:
- participant count and roles;
- rig family;
- pose family;
- clothing slot compatibility;
- location requirements;
- prop/anchor requirements;
- camera visibility requirements;
- culture/era tags;
- morphology/body-plan tags and numeric ranges;
- pack version.

If no complete compatible recipe exists, the composer must fall back deterministically to a simpler supported representation (portrait, silhouette, symbolic scene, or text-only event) rather than rendering broken layers.

## Determinism
- Never use `Math.random()`, system time or unordered collection iteration.
- Derive selection tokens from stable inputs.
- Weighted selection is allowed only after eligibility filtering.
- Save/replay must retain enough information (`eventId`, pack/version, token or resolved recipe id) to reproduce the same scene.

## Asset-pack rules
- One coherent visual style per pack.
- Assets within a pack share documented anchors, scale and rig conventions.
- Packs declare supported rig/pose/clothing/location/morphology combinations.
- Pack validation rejects missing anchors, unknown slots, duplicate ids and impossible compatibility references.
- Packs can extend the composer without changing simulation facts or core state.

## Architecture boundary
The generic composer belongs to the render/media side of the project and is content-neutral. Optional mature/adult visual packs can provide their own assets and compatibility metadata through the same logical scene interface. Removing an optional pack must not break the base game.

## Planned modules
- `core/scene` — scene request/recipe models, deterministic resolver, compatibility validator.
- `feature/scene-renderer` — Android/Compose rendering of resolved recipes.
- asset packs — data + local binary assets, versioned separately where practical.

## Performance
- Resolve recipes on demand and cache by deterministic scene key.
- Reuse decoded textures/sprites.
- Prefer atlases or other batched loading where appropriate.
- Keep the simulation independent from bitmap lifetime and renderer memory pressure.

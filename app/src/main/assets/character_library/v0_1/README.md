# Chronosphere character library v0.1

This folder contains the local/offline modular raster character library used by the Android app. Runtime rendering does not call an image generator, LLM, network service, or external API.

The previous `character_parts_v02.webp` was committed as a truncated RIFF/WebP stream, so Android decoded it to `null` and the UI silently displayed the old `ModularCharacterPortrait`. That behavior is removed. The current compact v03 raster bytes are stored losslessly as two base64 asset parts (`character_parts_v03_480.b64.00` and `.01`), concatenated and decoded locally before `BitmapFactory`. A damaged/missing primary pack now produces an obvious asset-error card instead of silently switching art styles.

The decoded v03 pack is 480x480 and contains deterministic female/male heads, wardrobe layers and canonical torso layers. `person.id` selects a stable visual family, head and garment; age can select the mature head. Dressed/partial states compose those raster layers.

The nine normalized adult female torso variants supplied for `UNDRESSED` are stored in one compact base64 asset (`female_undress_torsos_v01_384.b64.00`), decoding to a 384x450 3x3 atlas. They are reachable only for an adult female character in `UNDRESSED`; the same deterministic character head is composited above the selected torso, so changing wardrobe state does not replace character identity.

Legacy `character_parts_v01.webp`, `character_parts_v02.webp`, and the earlier focused lower-front atlas remain only as historical assets and are not used by the current renderer.

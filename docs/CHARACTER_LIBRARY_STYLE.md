# Style target: mature 2D animation

The first modular portrait library is intentionally restrained: normal adult head/body proportions, no chibi scaling, no oversized anime eyes, subdued palette, and soft cinematic shading.

The current v0.1 parts are renderer-native Compose vectors. They are placeholders for the *library contract*, not the final art ceiling. Future hand-drawn or generated transparent raster/vector parts can replace individual categories as long as the canonical anchors remain unchanged.

The game must assemble a portrait from layers deterministically by character identity. It must never select a completely unrelated precomposed face for a wardrobe/age change.

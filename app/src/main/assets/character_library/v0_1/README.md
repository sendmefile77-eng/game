# Chronosphere character library v0.1

This folder contains the first real offline modular 2D character library used by the Android app.

`character_parts_v02.webp` is a physically cut raster atlas prepared from the approved mature/semi-realistic character-library board. It contains female/male head variants, female/male wardrobe variants, canonical headless torso layers, plus reserved eye / nose / mouth detail regions for the next finer-grained face pass.

Runtime assembly is deterministic and completely local: `person.id` selects the same visual family, head and wardrobe every time; age can select the mature head variant; wardrobe changes only replace/remove the clothing layer. No runtime AI, no network access, and no simulation/save-format dependency on this concrete renderer.

The visual target is adult/semi-realistic animation: normal head/body proportions, restrained facial exaggeration, cinematic shading, and no chibi/oversized-anime styling.

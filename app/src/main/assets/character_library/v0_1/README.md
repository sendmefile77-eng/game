# Chronosphere character library v0.1

This folder defines the renderer-neutral contract for the first offline modular 2D character constructor.

The v0.1 Kotlin renderer intentionally ships with a small mature-animation preset library so the app can prove deterministic layer assembly without depending on runtime AI, network access, or a final portrait engine.

Future raster/vector replacements must preserve the canonical 320x480 coordinate system and anchors from `manifest.txt`. Parts are replaced category-by-category; `person.id` remains the identity seed.

The style target is adult/semi-realistic animation: normal head/body proportions, restrained facial exaggeration, cinematic shading, and no chibi/anime head scaling.

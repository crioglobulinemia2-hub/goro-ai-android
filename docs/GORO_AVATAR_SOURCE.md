# GORO humanoid — first-party model source

## Why this exists

The project had no externally supplied GLB for the approved GORO character. The build must not block on asking Matías to provide an asset he does not have. The repository therefore owns the geometry and rig source.

## Source of truth

- Geometry and GLB writer: `scripts/generate_goro_glb.py`
- Packaging/materialization: `scripts/prepare_avatar.py`
- Source asset path generated deterministically: `art/goro/goro_rigged.glb`
- Android runtime asset: `app/src/main/assets/goro/goro_rigged.glb`

The model is first-party parametric geometry, not an imported commercial model and not a raster image/sprite. The generated asset contains:
- A full humanoid shell with white/titanium/graphite/optical-blue materials and GORO chest mark.
- Hierarchical skeleton for torso, head, shoulders, elbows, wrists, hands, fingers, hips, knees and ankles.
- Ten face morph targets for visemes, eye blinks, brow raise and smile.
- Six animation clips: idle, listening, thinking, speaking, confirming and alert.
- Embedded material texture and GLB resources.

## Build behavior

If the source GLB is absent, `prepare_avatar.py` invokes the first-party generator and writes the deterministic result to the source path before copying the exact same bytes into Android assets. If a source GLB exists, it is validated and preserved byte-for-byte. The previous development-only fallback label is rejected.

## Acceptance boundary

A valid rig and GLB contract do **not** prove the visual model matches the approved GORO concept. The separate visual acceptance remains open until the app is rendered on Android and compared with the approved concept image. The generated model must not be called visually approved until that evidence exists.

No user-supplied 3D file is required to build or improve this model.

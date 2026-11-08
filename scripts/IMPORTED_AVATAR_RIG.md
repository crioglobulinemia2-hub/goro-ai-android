# Importing the approved character

`rig_imported_avatar.py` is an offline authoring utility. It takes the real static
GLB exported from TRELLIS and a JSON recipe measured against that exact model.
It does not generate replacement geometry or locate anatomy from a photograph.

The approved geometry is not available yet. No production recipe or authored
GLB has been produced by adding this utility.

Run it only after inspecting the actual export:

```sh
python scripts/rig_imported_avatar.py original.glb measured-recipe.json review/goro-rigged.glb
```

The output path must be new and different from the source. The original file is
never changed. The GLB is self-contained; its existing binary data, texture
resources, material definitions, triangle indices and UV accessors remain.
Positions and normals are baked from the source scene into world coordinates
before the translation-only rest skeleton is bound. Reflected transforms,
compressed primitives, sparse source attributes and node extensions must be
baked by the original authoring tool first; they are rejected rather than
silently discarded.

## Recipe fields

All coordinates below refer to the source scene's world coordinates, after its
node transforms. Do not copy coordinates from the development robot.

| Field | Contents |
| --- | --- |
| `source_sha256` | Exact SHA-256 of the untouched static GLB |
| `joints` | Parent-first objects with `name`, `parent`, and measured world `position` |
| `influences` | Objects with `joint`, world-space `start`/`end` and positive `radius` defining each skin influence capsule |
| `rigid_parts` | Optional boolean; chooses a single strongest influence for rigid armor. Default blends up to four influences |
| `morphs` | Map of required morph name to a list of measured region operations |
| `clips` | Map of required clip name to rotation tracks |

Each facial operation has `center`, positive ellipsoid `radius`, and optional
`translation` and positive `scale`. Influence tapers to zero at the region's
boundary. A morph must actually move source vertices. Several operations can
be combined for the two brows or separate parts of a mouth. These operations
are an authoring mechanism, not a claim that facial topology is suitable: an
image-textured mouth may require mesh editing before believable speech is
possible. Morph normal deltas are not generated; inspect shading during facial
deformation and author them in a 3D editor if required.

Each clip track has a `joint`, strictly increasing nonnegative `times`, and a
matching list of unit-quaternion `rotations` in local xyzw order. Rotations are
relative to the translation-only rest skeleton. Constant rotations are rejected.
The six clip names are `idle`, `listening`, `thinking`, `speaking`, `confirming`
and `alert`; a valid name does not establish that the gesture looks correct.

The fourteen required joint names and ten facial target names are imported from
`prepare_avatar.py`. Additional joints are allowed. No skin vertex may be left
outside all influence regions. Weights are normalised and limited to four
influences. Neutral inverse-bind matrices retain the imported character's rest
shape.

## Acceptance and packaging

The generated asset records the source SHA and original asset metadata, with
`visual_acceptance=false`. It passes the renderer's structural contract but is
not copied into the app or presented as an approved character. Inspect the
neutral pose against the approved reference and review all six clips and ten
morphs on the actual geometry, including hands, joints and silhouette. Then
perform Android rendering and performance checks. Only an accepted authored
asset belongs at `art/goro/goro_rigged.glb`; `prepare_avatar.py` will package it
unchanged.

The small synthetic triangle in `test_imported_avatar_rig.py` only tests source
preservation, neutral binding and rejection paths. It is never production art.

```sh
python -m unittest discover -s scripts -p 'test_imported_avatar_rig.py' -v
```

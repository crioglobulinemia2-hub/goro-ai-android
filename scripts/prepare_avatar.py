#!/usr/bin/env python3
"""Package the project-owned GORO humanoid; generate its first-party GLB when absent."""
import argparse
import hashlib
import json
import math
import pathlib
import struct
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
AUTHORED_RELATIVE = "art/goro/goro_rigged.glb"
RUNTIME_RELATIVE = "app/src/main/assets/goro/goro_rigged.glb"
PROCEDURAL_RELATIVE = "build/generated-avatar/goro_procedural.glb"
PROCEDURAL_GENERATOR = "GORO procedural articulated robot authoring"
BONES = {"root", "pelvis", "spine", "chest", "neck", "head",
         "left_shoulder", "left_elbow", "left_wrist", "left_hand",
         "right_shoulder", "right_elbow", "right_wrist", "right_hand"}
MORPHS = {"viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U",
          "viseme_MBP", "eye_blink_left", "eye_blink_right", "brow_raise", "smile"}
CLIPS = {"idle", "listening", "thinking", "speaking", "confirming", "alert"}

def require(condition, message):
    if not condition:
        raise ValueError(message)

def validate_contract(data, authored=False):
    """Validate packaging and the existing renderer contract, not artistic approval."""
    require(len(data) >= 20 and data[:4] == b"glTF", "Avatar is not a GLB")
    version, length, json_length, chunk_type = struct.unpack_from("<IIII", data, 4)
    require(version == 2 and length == len(data), "Invalid GLB version or length")
    require(chunk_type == 0x4E4F534A and 20 + json_length <= len(data), "Invalid GLB JSON chunk")
    binary_offset = 20 + json_length
    require(binary_offset + 8 <= len(data), "Missing GLB binary chunk")
    binary_length, binary_type = struct.unpack_from("<II", data, binary_offset)
    require(binary_type == 0x004E4942 and binary_offset + 8 + binary_length == len(data),
            "Invalid GLB binary chunk")
    doc = json.loads(data[20:20 + json_length].decode("utf-8"))
    require(doc.get("asset", {}).get("version") == "2.0", "Unsupported glTF version")
    if authored:
        require(doc["asset"].get("generator") != PROCEDURAL_GENERATOR,
                "The development procedural robot cannot be the authored asset")
    nodes, meshes, skins = doc.get("nodes", []), doc.get("meshes", []), doc.get("skins", [])
    require(bool(skins), "Avatar has no skin")
    joint_names = set()
    for skin in skins:
        for joint in skin.get("joints", []):
            require(isinstance(joint, int) and 0 <= joint < len(nodes), "Invalid skin joint")
            joint_names.add(nodes[joint].get("name"))
    require(BONES.issubset(joint_names), "Avatar is missing required skin joints")
    faces = [n for n in nodes if n.get("name") == "GORO_Face_Morphs"]
    require(len(faces) == 1, "Avatar must have one GORO_Face_Morphs node")
    face = faces[0]
    mesh_index, skin_index = face.get("mesh", -1), face.get("skin", -1)
    require(isinstance(mesh_index, int) and 0 <= mesh_index < len(meshes), "Face mesh is missing")
    require(isinstance(skin_index, int) and 0 <= skin_index < len(skins), "Face skin is missing")
    face_mesh = meshes[mesh_index]
    names = face_mesh.get("extras", {}).get("targetNames", [])
    require(all(names.count(name) == 1 for name in MORPHS), "Required facial morph names are missing or duplicated")
    require(bool(face_mesh.get("primitives")), "Face has no primitives")
    for primitive in face_mesh["primitives"]:
        require(len(primitive.get("targets", [])) == len(names), "Face target count differs from target names")
    for mesh in meshes:
        for primitive in mesh.get("primitives", []):
            require({"POSITION", "TEXCOORD_0", "JOINTS_0", "WEIGHTS_0"}.issubset(primitive.get("attributes", {})),
                    "Avatar primitive is missing render/skin attributes")
    animations = doc.get("animations", [])
    clip_names = [a.get("name", "").strip().lower() for a in animations]
    require(all(clip_names.count(name) == 1 for name in CLIPS), "Required animation clips are missing or duplicated")
    accessors = doc.get("accessors", [])
    for animation in animations:
        require(bool(animation.get("samplers")) and bool(animation.get("channels")), "Empty animation clip")
        for sampler in animation["samplers"]:
            index = sampler.get("input", -1)
            require(isinstance(index, int) and 0 <= index < len(accessors), "Animation input is missing")
            accessor = accessors[index]
            require(accessor.get("type") == "SCALAR", "Animation input must be scalar")
            start, end = accessor.get("min", [None])[0], accessor.get("max", [None])[0]
            require(isinstance(start, (int, float)) and isinstance(end, (int, float))
                    and math.isfinite(start) and math.isfinite(end) and 0 <= start <= end,
                    "Invalid animation duration")
    require(bool(doc.get("materials")) and bool(doc.get("textures")), "Missing PBR materials/textures")
    for resource in doc.get("buffers", []) + doc.get("images", []):
        uri = resource.get("uri")
        require(uri is None or uri.startswith("data:"), "External avatar resources must be embedded")
    return doc

def prepare_avatar(root=ROOT, allow_procedural=False):
    root = pathlib.Path(root)
    authored = root / AUTHORED_RELATIVE
    runtime = root / RUNTIME_RELATIVE
    if authored.is_file():
        data = authored.read_bytes()
        validate_contract(data, authored=True)
        kind = "authored-source"
    else:
        # Matías no dispone de un GLB externo. Build the character from the
        # project's own geometry/rig authoring source instead of asking him
        # for an asset or silently shipping an empty avatar.
        subprocess.run([sys.executable, str(root / "scripts/generate_goro_glb.py")], check=True)
        data = (root / PROCEDURAL_RELATIVE).read_bytes()
        doc = validate_contract(data)
        require(doc.get("asset", {}).get("generator") ==
                "GORO first-party parametric articulated character authoring",
                "Generated avatar is not the first-party GORO character")
        validate_contract(data, authored=True)
        authored.parent.mkdir(parents=True, exist_ok=True)
        temporary_source = authored.with_suffix(".glb.tmp")
        temporary_source.write_bytes(data)
        temporary_source.replace(authored)
        kind = "first-party-parametric-authored"
    runtime.parent.mkdir(parents=True, exist_ok=True)
    temporary = runtime.with_suffix(".glb.tmp")
    temporary.write_bytes(data)
    temporary.replace(runtime)
    return {"source_kind": kind, "sha256": hashlib.sha256(data).hexdigest(), "size_bytes": len(data)}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-procedural", action="store_true",
                        help="Deprecated compatibility flag; the first-party GORO GLB is generated automatically")
    args = parser.parse_args()
    try:
        print(json.dumps(prepare_avatar(allow_procedural=args.allow_procedural)))
    except (OSError, ValueError, TypeError, KeyError, IndexError, AttributeError, struct.error) as exc:
        raise SystemExit("Avatar preparation failed: " + str(exc))

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Bind a real, static GLB using an explicit, source-pinned authoring recipe.

This tool does not invent character geometry or infer anatomical landmarks. The
recipe must be authored against the imported model's world coordinates. Neutral
geometry, UVs, indices and embedded materials are retained, and the original GLB
is never overwritten. A completed export still needs deformation and visual QA.
"""
import argparse
import copy
import hashlib
import json
import math
import pathlib
import struct

from prepare_avatar import BONES, CLIPS, MORPHS, PROCEDURAL_GENERATOR, require, validate_contract

WIDTHS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
FORMATS = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
IDENTITY = [1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.]


def vector(value, size=3):
    require(isinstance(value, list) and len(value) == size, "Invalid vector")
    require(all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x)
                for x in value), "Non-finite vector")
    return [float(x) for x in value]


def multiply(a, b):
    return [sum(a[k * 4 + row] * b[col * 4 + k] for k in range(4))
            for col in range(4) for row in range(4)]


def point(matrix, value):
    return [sum(matrix[col * 4 + row] * value[col] for col in range(3)) + matrix[12 + row]
            for row in range(3)]


def node_matrix(node):
    if "matrix" in node:
        require(not any(k in node for k in ("translation", "rotation", "scale")), "Mixed node transform")
        matrix = vector(node["matrix"], 16)
        require([matrix[i] for i in (3, 7, 11, 15)] == [0., 0., 0., 1.], "Source matrix must be affine")
        return matrix
    x, y, z, w = vector(node.get("rotation", [0, 0, 0, 1]), 4)
    require(abs(x*x+y*y+z*z+w*w-1) < 0.001, "Non-unit node quaternion")
    scale = vector(node.get("scale", [1, 1, 1]))
    require(all(x > 0 for x in scale), "Negative/zero source scale needs baking before rigging")
    result = [1-2*y*y-2*z*z, 2*x*y+2*z*w, 2*x*z-2*y*w, 0,
              2*x*y-2*z*w, 1-2*x*x-2*z*z, 2*y*z+2*x*w, 0,
              2*x*z+2*y*w, 2*y*z-2*x*w, 1-2*x*x-2*y*y, 0,
              *vector(node.get("translation", [0, 0, 0])), 1]
    for col in range(3):
        for row in range(3):
            result[col * 4 + row] *= scale[col]
    return result


def normal(matrix, value):
    # Cofactors form the inverse-transpose of the affine 3x3 (normalised below).
    a, b, c = matrix[0], matrix[4], matrix[8]
    d, e, f = matrix[1], matrix[5], matrix[9]
    g, h, i = matrix[2], matrix[6], matrix[10]
    cofactor = [[e*i-f*h, f*g-d*i, d*h-e*g],
                [c*h-b*i, a*i-c*g, b*g-a*h],
                [b*f-c*e, c*d-a*f, a*e-b*d]]
    determinant = a*cofactor[0][0] + b*cofactor[0][1] + c*cofactor[0][2]
    require(determinant > 1e-12, "Reflected/singular source transform needs baking before rigging")
    output = [sum(cofactor[row][col] * value[col] for col in range(3)) for row in range(3)]
    length = math.sqrt(sum(x*x for x in output))
    require(length > 1e-12, "Zero source normal")
    return [x/length for x in output]


def tangent(matrix, value, transformed_normal):
    output = [sum(matrix[col*4+row]*value[col] for col in range(3)) for row in range(3)]
    projection = sum(a*b for a, b in zip(output, transformed_normal))
    output = [a-projection*b for a, b in zip(output, transformed_normal)]
    length = math.sqrt(sum(x*x for x in output))
    require(length > 1e-12, "Degenerate source tangent")
    return [x/length for x in output] + [value[3]]


class Glb:
    def __init__(self, data):
        require(len(data) >= 28 and data[:4] == b"glTF", "Source is not GLB")
        version, length, count, kind = struct.unpack_from("<IIII", data, 4)
        require(version == 2 and length == len(data) and kind == 0x4e4f534a, "Invalid source GLB")
        offset = 20 + count
        require(offset + 8 <= len(data), "Source has no binary data")
        size, kind = struct.unpack_from("<II", data, offset)
        require(kind == 0x004e4942 and offset+8+size == len(data), "Invalid source binary chunk")
        self.doc = json.loads(data[20:offset])
        self.binary = bytearray(data[offset+8:])
        require(len(self.doc.get("buffers", [])) == 1, "Expected one embedded source buffer")
        require(not self.doc["buffers"][0].get("uri"), "External source buffer")
        require(not self.doc.get("skins") and not self.doc.get("animations"), "Source is already animated; preserve its authoring")
        require(self.doc.get("asset", {}).get("generator") != PROCEDURAL_GENERATOR,
                "Development procedural robot cannot become the imported source")
        for resource in self.doc.get("images", []):
            require(resource.get("uri") is None or resource["uri"].startswith("data:"), "External source image")

    def read(self, index):
        require(isinstance(index, int) and not isinstance(index, bool) and 0 <= index < len(self.doc["accessors"]), "Invalid accessor index")
        accessor = self.doc["accessors"][index]
        require("sparse" not in accessor and "bufferView" in accessor, "Bake sparse source accessor first")
        require(not accessor.get("normalized"), "Normalised source attributes need explicit conversion")
        view = self.doc["bufferViews"][accessor["bufferView"]]
        require(view.get("buffer", 0) == 0, "External source accessor")
        count = accessor["count"]
        fmt = "<" + FORMATS[accessor["componentType"]] * WIDTHS[accessor["type"]]
        width = struct.calcsize(fmt)
        stride = view.get("byteStride", width)
        offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        require(count > 0 and stride >= width and offset >= 0
                and accessor.get("byteOffset", 0) >= 0
                and accessor.get("byteOffset", 0) + (count-1)*stride+width <= view["byteLength"]
                and view.get("byteOffset", 0) + view["byteLength"] <= len(self.binary), "Invalid source accessor bounds")
        return [list(struct.unpack_from(fmt, self.binary, offset+n*stride)) for n in range(count)]

    def append(self, rows, kind, component=5126, target=None, bounds=False):
        width = WIDTHS[kind]
        require(bool(rows) and all(len(row) == width for row in rows), "Invalid authored attribute")
        require(all(math.isfinite(x) for row in rows for x in row), "Non-finite authored attribute")
        self.binary.extend(b"\0" * (-len(self.binary) % 4))
        offset = len(self.binary)
        fmt = "<" + FORMATS[component] * width
        for row in rows:
            self.binary.extend(struct.pack(fmt, *row))
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(self.binary)-offset}
        if target is not None:
            view["target"] = target
        view_id = len(self.doc.setdefault("bufferViews", []))
        self.doc["bufferViews"].append(view)
        accessor = {"bufferView": view_id, "componentType": component, "count": len(rows), "type": kind}
        if bounds:
            accessor["min"] = [min(row[i] for row in rows) for i in range(width)]
            accessor["max"] = [max(row[i] for row in rows) for i in range(width)]
        index = len(self.doc.setdefault("accessors", []))
        self.doc["accessors"].append(accessor)
        return index

    def encode(self):
        self.doc["buffers"] = [{"byteLength": len(self.binary)}]
        binary = bytes(self.binary) + b"\0" * (-len(self.binary) % 4)
        text = json.dumps(self.doc, separators=(",", ":")).encode()
        text += b" " * (-len(text) % 4)
        return (b"glTF" + struct.pack("<II", 2, 28+len(text)+len(binary)) +
                struct.pack("<I4s", len(text), b"JSON") + text +
                struct.pack("<I4s", len(binary), b"BIN\0") + binary)


def source_instances(doc):
    nodes = doc.get("nodes", [])
    roots = doc["scenes"][doc.get("scene", 0)]["nodes"]
    found = []
    def walk(index, parent, ancestors):
        require(index not in ancestors and 0 <= index < len(nodes), "Cyclic/invalid source scene")
        node = nodes[index]
        require("skin" not in node, "Source skin must not be rebound")
        require(not node.get("extensions"), "Bake source node extensions/instances first")
        matrix = multiply(parent, node_matrix(node))
        if "mesh" in node:
            found.append((node["mesh"], matrix))
        for child in node.get("children", []):
            walk(child, matrix, ancestors | {index})
    for root in roots:
        walk(root, IDENTITY, set())
    require(bool(found), "Source scene has no geometry")
    return found


def build_joints(recipe):
    entries = recipe["joints"]
    require(0 < len(entries) <= 256, "Invalid skeleton size")
    names = [joint["name"] for joint in entries]
    require(len(names) == len(set(names)) and BONES.issubset(names), "Missing/duplicate required joints")
    positions, nodes, inverse = {}, [], []
    for index, entry in enumerate(entries):
        name, parent = entry["name"], entry.get("parent")
        require(parent is None or parent in positions, "Joints must be in parent-first order")
        require((name == "root") == (parent is None), "Skeleton must have one root")
        position = vector(entry["position"])
        origin = positions.get(parent, [0., 0., 0.])
        nodes.append({"name": name, "translation": [position[i]-origin[i] for i in range(3)]})
        if parent:
            nodes[names.index(parent)].setdefault("children", []).append(index)
        positions[name] = position
        inverse.append(IDENTITY[:12] + [-x for x in position] + [1.])
    return names, nodes, inverse


def capsule_distance(position, start, end):
    axis = [end[i]-start[i] for i in range(3)]
    length2 = sum(x*x for x in axis)
    t = min(1., max(0., sum((position[i]-start[i])*axis[i] for i in range(3))/length2)) if length2 else 0.
    return math.sqrt(sum((position[i]-start[i]-axis[i]*t)**2 for i in range(3)))


def skin_vertices(positions, recipe, joint_names):
    influences = recipe["influences"]
    require(bool(influences), "Explicit skin influence segments are required")
    segments = []
    for entry in influences:
        require(entry["joint"] in joint_names, "Unknown skin joint")
        radius = float(entry["radius"])
        require(math.isfinite(radius) and radius > 0, "Invalid influence radius")
        segments.append((joint_names.index(entry["joint"]), vector(entry["start"]), vector(entry["end"]), radius))
    joints, weights = [], []
    for position in positions:
        choices = {}
        for joint, start, end, radius in segments:
            distance = capsule_distance(position, start, end)/radius
            if distance < 1:
                choices[joint] = max(choices.get(joint, 0.), (1-distance)**2)
        require(bool(choices), "A source vertex is outside every authored influence region")
        selected = sorted(choices.items(), key=lambda pair: (-pair[1], pair[0]))[:4]
        if recipe.get("rigid_parts", False):
            selected = selected[:1]
        total = sum(weight for _, weight in selected)
        joints.append([joint for joint, _ in selected] + [0]*(4-len(selected)))
        weights.append([weight/total for _, weight in selected] + [0.]*(4-len(selected)))
    return joints, weights


def morph_vertices(positions, operation):
    center = vector(operation["center"])
    radius = vector(operation["radius"])
    require(all(x > 0 for x in radius), "Invalid facial region radius")
    translation = vector(operation.get("translation", [0, 0, 0]))
    scale = vector(operation.get("scale", [1, 1, 1]))
    require(all(x > 0 for x in scale), "Facial scale must stay positive")
    result = []
    for position in positions:
        distance2 = sum(((position[i]-center[i])/radius[i])**2 for i in range(3))
        falloff = max(0., 1-distance2)**2
        result.append([falloff*(translation[i]+(position[i]-center[i])*(scale[i]-1)) for i in range(3)])
    return result


def rig(source, recipe):
    sha = hashlib.sha256(source).hexdigest()
    require(recipe.get("source_sha256") == sha, "Authoring recipe does not match the exact source GLB")
    asset = Glb(source)
    original_asset = copy.deepcopy(asset.doc.get("asset", {}))
    names, nodes, inverse = build_joints(recipe)
    morph_names = list(recipe["morphs"])
    require(set(morph_names) == MORPHS, "Recipe must define every required facial morph exactly once")
    primitives, effect = [], {name: 0. for name in morph_names}
    for mesh_index, matrix in source_instances(asset.doc):
        for original in asset.doc["meshes"][mesh_index]["primitives"]:
            require(original.get("mode", 4) == 4 and not original.get("targets"), "Expected static triangle primitives")
            require(not original.get("extensions"), "Bake compressed/extended source primitives first")
            primitive = copy.deepcopy(original)
            attributes = primitive["attributes"]
            require({"POSITION", "NORMAL", "TEXCOORD_0"}.issubset(attributes), "Source needs positions, normals and UVs")
            positions = [point(matrix, value) for value in asset.read(attributes["POSITION"])]
            normals = [normal(matrix, value) for value in asset.read(attributes["NORMAL"])]
            require(len(normals) == len(positions), "Source normal count differs")
            attributes["POSITION"] = asset.append(positions, "VEC3", target=34962, bounds=True)
            attributes["NORMAL"] = asset.append(normals, "VEC3", target=34962)
            # Tangents need the same world-space transform; their handedness is retained.
            if "TANGENT" in attributes:
                original_tangents = asset.read(attributes["TANGENT"])
                require(len(original_tangents) == len(normals), "Source tangent count differs")
                tangents = [tangent(matrix, value, norm) for value, norm in zip(original_tangents, normals)]
                attributes["TANGENT"] = asset.append(tangents, "VEC4", target=34962)
            joints, weights = skin_vertices(positions, recipe, names)
            attributes["JOINTS_0"] = asset.append(joints, "VEC4", component=5123, target=34962)
            attributes["WEIGHTS_0"] = asset.append(weights, "VEC4", target=34962)
            primitive["targets"] = []
            for name in morph_names:
                operations = recipe["morphs"][name]
                require(isinstance(operations, list) and bool(operations), "Empty facial authoring recipe")
                delta = [[0., 0., 0.] for _ in positions]
                for operation in operations:
                    authored = morph_vertices(positions, operation)
                    delta = [[a+b for a, b in zip(old, new)] for old, new in zip(delta, authored)]
                effect[name] += sum(abs(x) for row in delta for x in row)
                primitive["targets"].append({"POSITION": asset.append(delta, "VEC3", target=34962)})
            primitives.append(primitive)
    require(all(value > 1e-8 for value in effect.values()), "One or more facial regions affect no source vertices")
    nodes.append({"name": "GORO_Face_Morphs", "mesh": 0, "skin": 0})
    asset.doc["nodes"] = nodes
    asset.doc["scene"] = 0
    asset.doc["scenes"] = [{"nodes": [names.index("root"), len(nodes)-1]}]
    asset.doc["meshes"] = [{"name": "GORO_Imported_Character", "primitives": primitives,
                             "weights": [0.]*len(morph_names), "extras": {"targetNames": morph_names}}]
    asset.doc["skins"] = [{"name": "GORO_Skeleton", "joints": list(range(len(names))),
                           "skeleton": names.index("root"), "inverseBindMatrices": asset.append(inverse, "MAT4")}]
    animations = []
    require(set(recipe["clips"]) == CLIPS, "Recipe must define every required clip exactly once")
    for name, tracks in recipe["clips"].items():
        channels, samplers = [], []
        require(bool(tracks), "Empty clip recipe")
        for track in tracks:
            require(track["joint"] in names, "Unknown animation joint")
            times = track["times"]
            values = [vector(q, 4) for q in track["rotations"]]
            require(len(times) >= 2 and len(times) == len(values), "Invalid animation keyframes")
            require(all(isinstance(t, (int, float)) and math.isfinite(t) and t >= 0 for t in times)
                    and all(a < b for a, b in zip(times, times[1:])), "Non-increasing animation times")
            require(all(abs(sum(x*x for x in q)-1.) < 0.001 for q in values), "Non-unit animation quaternion")
            require(any(sum((a-b)**2 for a, b in zip(values[0], q)) > 1e-10 and
                        sum((a+b)**2 for a, b in zip(values[0], q)) > 1e-10 for q in values[1:]),
                    "Animation track contains no rotation")
            samplers.append({"input": asset.append([[t] for t in times], "SCALAR", bounds=True),
                             "output": asset.append(values, "VEC4"), "interpolation": "LINEAR"})
            channels.append({"sampler": len(samplers)-1, "target": {"node": names.index(track["joint"]), "path": "rotation"}})
        animations.append({"name": name, "samplers": samplers, "channels": channels})
    asset.doc["animations"] = animations
    asset.doc["asset"] = {"version": "2.0", "generator": "GORO imported geometry rig authoring",
                           "extras": {"source_sha256": sha, "source_asset": original_asset,
                                      "visual_acceptance": False}}
    result = asset.encode()
    validate_contract(result, authored=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=pathlib.Path)
    parser.add_argument("recipe", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()
    require(args.source.resolve() != args.output.resolve(), "Never overwrite the source export")
    require(not args.output.exists(), "Output already exists; retain previous authoring")
    output = rig(args.source.read_bytes(), json.loads(args.recipe.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as stream:
        stream.write(output)
    print(json.dumps({"path": str(args.output), "sha256": hashlib.sha256(output).hexdigest(),
                      "bytes": len(output), "visual_acceptance": False}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, IndexError, TypeError, OSError, struct.error) as error:
        raise SystemExit("Imported avatar rigging failed: " + str(error))

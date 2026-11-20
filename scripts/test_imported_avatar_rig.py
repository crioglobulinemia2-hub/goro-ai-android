"""Small geometry fixtures exercise import preservation, binding and hard failures."""
import copy
import hashlib
import json
import math
import struct
import unittest

import rig_imported_avatar as importer


def static_fixture(generator="static-test-triangle"):
    # Explicitly synthetic test art; never written to art/ or runtime assets.
    asset = importer.Glb.__new__(importer.Glb)
    asset.binary = bytearray()
    asset.doc = {"asset": {"version": "2.0", "generator": generator},
                 "buffers": [{"byteLength": 0}], "nodes": [{"mesh": 0, "translation": [2, 3, 4]}],
                 "scenes": [{"nodes": [0]}], "scene": 0}
    positions = asset.append([[-.1, 0, 0], [.1, 0, 0], [0, .2, 0]], "VEC3", bounds=True)
    normals = asset.append([[0, 0, 1]]*3, "VEC3")
    uv = asset.append([[0, 0], [1, 0], [.5, 1]], "VEC2")
    indices = asset.append([[0], [1], [2]], "SCALAR", component=5123)
    asset.doc["meshes"] = [{"primitives": [{"attributes": {"POSITION": positions, "NORMAL": normals,
                                                              "TEXCOORD_0": uv}, "indices": indices, "material": 0}]}]
    image_view = len(asset.doc["bufferViews"])
    image = b"embedded-image-test-bytes"
    asset.doc["bufferViews"].append({"buffer": 0, "byteOffset": len(asset.binary), "byteLength": len(image)})
    asset.binary.extend(image)
    asset.doc["images"] = [{"bufferView": image_view, "mimeType": "image/png"}]
    asset.doc["textures"] = [{"source": 0}]
    asset.doc["materials"] = [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}]
    return asset.encode()


def recipe_for(source):
    names = ["root"] + sorted(importer.BONES-{"root"})
    joints = [{"name": name, "parent": None if name == "root" else "root",
               "position": [2, 3, 4] if name == "head" else [0, 0, 0]} for name in names]
    return {"source_sha256": hashlib.sha256(source).hexdigest(), "joints": joints,
            "influences": [{"joint": "head", "start": [2, 3, 4], "end": [2, 3.2, 4], "radius": 1}],
            "morphs": {name: [{"center": [2, 3, 4], "radius": [1, 1, 1], "translation": [0, .01, 0]}]
                       for name in sorted(importer.MORPHS)},
            "clips": {name: [{"joint": "head", "times": [0, 1, 2],
                               "rotations": [[0, 0, 0, 1], [0, 0, math.sin(.1), math.cos(.1)], [0, 0, 0, 1]]}]
                      for name in sorted(importer.CLIPS)}}


def output_data(data):
    count = struct.unpack_from("<I", data, 12)[0]
    asset = importer.Glb.__new__(importer.Glb)
    asset.doc = json.loads(data[20:20+count])
    asset.binary = bytearray(data[28+count:])
    return asset


class ImportedAvatarRigTests(unittest.TestCase):
    def test_world_geometry_and_neutral_binding_preserve_the_imported_character(self):
        source = static_fixture()
        original = importer.Glb(source)
        recipe = recipe_for(source)
        result = output_data(importer.rig(source, recipe))
        self.assertEqual(original.binary, result.binary[:len(original.binary)])
        self.assertEqual(original.doc["images"], result.doc["images"])
        self.assertEqual(original.doc["materials"], result.doc["materials"])
        self.assertEqual(original.doc["textures"], result.doc["textures"])
        primitive = result.doc["meshes"][0]["primitives"][0]
        original_primitive = original.doc["meshes"][0]["primitives"][0]
        self.assertEqual(original_primitive["indices"], primitive["indices"])
        self.assertEqual(original_primitive["attributes"]["TEXCOORD_0"], primitive["attributes"]["TEXCOORD_0"])
        positions = result.read(primitive["attributes"]["POSITION"])
        for actual, expected in zip(positions, [[1.9, 3, 4], [2.1, 3, 4], [2, 3.2, 4]]):
            for a, b in zip(actual, expected):
                self.assertAlmostEqual(a, b, places=6)
        head = [j["name"] for j in recipe["joints"]].index("head")
        self.assertEqual([[head, 0, 0, 0]]*3, result.read(primitive["attributes"]["JOINTS_0"]))
        self.assertEqual([[1., 0., 0., 0.]]*3, result.read(primitive["attributes"]["WEIGHTS_0"]))
        inverse = result.read(result.doc["skins"][0]["inverseBindMatrices"])[head]
        restored = importer.multiply(importer.node_matrix(result.doc["nodes"][head]), inverse)
        self.assertEqual(importer.IDENTITY, restored)
        self.assertFalse(result.doc["asset"]["extras"]["visual_acceptance"])
        self.assertEqual(hashlib.sha256(source).hexdigest(), result.doc["asset"]["extras"]["source_sha256"])
        for target in primitive["targets"]:
            self.assertGreater(sum(abs(x) for row in result.read(target["POSITION"]) for x in row), 0)

    def test_wrong_source_and_procedural_source_are_rejected(self):
        source = static_fixture()
        recipe = recipe_for(source)
        recipe["source_sha256"] = "0"*64
        with self.assertRaisesRegex(ValueError, "exact source"):
            importer.rig(source, recipe)
        procedural = static_fixture(importer.PROCEDURAL_GENERATOR)
        with self.assertRaisesRegex(ValueError, "procedural"):
            importer.rig(procedural, recipe_for(procedural))

    def test_unbound_vertex_or_empty_morph_is_rejected(self):
        source = static_fixture()
        recipe = recipe_for(source)
        recipe["influences"][0]["radius"] = .01
        with self.assertRaisesRegex(ValueError, "outside every"):
            importer.rig(source, recipe)
        recipe = recipe_for(source)
        recipe["morphs"]["viseme_A"][0]["center"] = [100, 100, 100]
        with self.assertRaisesRegex(ValueError, "affect no source vertices"):
            importer.rig(source, recipe)

    def test_stationary_animation_and_nonfinite_landmark_are_rejected(self):
        source = static_fixture()
        recipe = recipe_for(source)
        recipe["clips"]["idle"][0]["rotations"] = [[0, 0, 0, 1]]*3
        with self.assertRaisesRegex(ValueError, "contains no rotation"):
            importer.rig(source, recipe)
        recipe = recipe_for(source)
        recipe["joints"][1]["position"][0] = float("nan")
        with self.assertRaisesRegex(ValueError, "Non-finite"):
            importer.rig(source, recipe)


if __name__ == "__main__":
    unittest.main()

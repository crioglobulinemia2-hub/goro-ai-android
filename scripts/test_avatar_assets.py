"""Packaging policy tests use minimal metadata fixtures, never production robot art."""
import json
import pathlib
import struct
import tempfile
import unittest
from unittest.mock import patch

import prepare_avatar as avatar
import release_gate
import generate_goro_glb as generator

def fixture(generator="authored-test-fixture"):
    nodes = [{"name": name} for name in sorted(avatar.BONES)]
    joints = list(range(len(nodes)))
    nodes.append({"name": "GORO_Face_Morphs", "mesh": 1, "skin": 0})
    attributes = {name: 0 for name in ("POSITION", "TEXCOORD_0", "JOINTS_0", "WEIGHTS_0")}
    names = sorted(avatar.MORPHS)
    doc = {
        "asset": {"version": "2.0", "generator": generator},
        "nodes": nodes, "skins": [{"joints": joints}],
        "meshes": [{"primitives": [{"attributes": attributes}]},
                   {"extras": {"targetNames": names},
                    "primitives": [{"attributes": attributes, "targets": [{} for _ in names]}]}],
        "animations": [{"name": name, "samplers": [{"input": 0, "output": 0}],
                        "channels": [{"sampler": 0}]} for name in sorted(avatar.CLIPS)],
        "accessors": [{"type": "SCALAR", "min": [0], "max": [2.75]}],
        "materials": [{}], "textures": [{}], "buffers": [{"byteLength": 4}],
    }
    text = json.dumps(doc).encode("utf-8")
    text += b" " * (-len(text) % 4)
    return (b"glTF" + struct.pack("<II", 2, 32 + len(text)) +
            struct.pack("<I4s", len(text), b"JSON") + text +
            struct.pack("<I4s", 4, b"BIN\x00") + b"\x00" * 4)

class AvatarSourceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.authored = self.root / avatar.AUTHORED_RELATIVE
        self.runtime = self.root / avatar.RUNTIME_RELATIVE

    def write(self, path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def test_authored_source_is_preserved_without_running_generator(self):
        original = fixture()
        self.write(self.authored, original)
        with patch.object(avatar.subprocess, "run") as generate:
            report = avatar.prepare_avatar(self.root, allow_procedural=True)
        generate.assert_not_called()
        self.assertEqual(original, self.authored.read_bytes())
        self.assertEqual(original, self.runtime.read_bytes())
        self.assertEqual("authored-source", report["source_kind"])

    def test_invalid_authored_source_never_falls_back_or_overwrites_runtime(self):
        self.write(self.authored, b"invalid export")
        self.write(self.runtime, b"previous asset")
        with patch.object(avatar.subprocess, "run") as generate:
            with self.assertRaises(ValueError):
                avatar.prepare_avatar(self.root, allow_procedural=True)
        generate.assert_not_called()
        self.assertEqual(b"previous asset", self.runtime.read_bytes())

    def test_missing_glb_is_generated_from_first_party_source(self):
        authored_model = fixture("GORO first-party parametric articulated character authoring")
        def generate(*args, **kwargs):
            self.write(self.root / avatar.PROCEDURAL_RELATIVE, authored_model)
        with patch.object(avatar.subprocess, "run", side_effect=generate) as called:
            report = avatar.prepare_avatar(self.root)
        called.assert_called_once()
        self.assertEqual(authored_model, self.authored.read_bytes())
        self.assertEqual(authored_model, self.runtime.read_bytes())
        self.assertEqual("first-party-parametric-authored", report["source_kind"])

    def test_development_generator_output_is_not_accepted_as_first_party_asset(self):
        development = fixture(avatar.PROCEDURAL_GENERATOR)
        def generate(*args, **kwargs):
            self.write(self.root / avatar.PROCEDURAL_RELATIVE, development)
        with patch.object(avatar.subprocess, "run", side_effect=generate):
            with self.assertRaisesRegex(ValueError, "not the first-party"):
                avatar.prepare_avatar(self.root)
        self.assertFalse(self.authored.exists())
        self.assertFalse(self.runtime.exists())

    def test_development_generator_label_is_rejected_as_authored(self):
        self.write(self.authored, fixture(avatar.PROCEDURAL_GENERATOR))
        with self.assertRaisesRegex(ValueError, "procedural"):
            avatar.prepare_avatar(self.root)

    def test_release_check_requires_the_exact_authored_source(self):
        original = fixture()
        self.write(self.runtime, original)
        with patch.object(release_gate, "ASSET", self.runtime), patch.object(release_gate, "AUTHORED_ASSET", self.authored):
            self.assertFalse(release_gate.glb_report()[0])
            self.write(self.authored, original)
            self.assertTrue(release_gate.glb_report()[0])
            self.write(self.runtime, fixture("different-export"))
            self.assertFalse(release_gate.glb_report()[0])

    def test_each_state_has_visible_full_body_gestures(self):
        clips = dict(generator.CLIP_DEFS)
        self.assertEqual(set(avatar.CLIPS), set(clips))
        for state, targets in clips.items():
            with self.subTest(state=state):
                self.assertGreaterEqual(len(targets), 4)
                minimum = 6 if state == "idle" else 18
                self.assertTrue(any(max(abs(v) for v in values) >= minimum
                                    for values in targets.values()))
        self.assertEqual((0, 0, 1), generator.ANIMATION_AXES["right_shoulder"])
        self.assertEqual((0, 0, 1), generator.ANIMATION_AXES["left_elbow"])

    def test_truncated_binary_is_rejected(self):
        with self.assertRaises(ValueError):
            avatar.validate_contract(fixture()[:-1])

if __name__ == "__main__":
    unittest.main()

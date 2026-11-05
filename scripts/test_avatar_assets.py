"""Packaging policy tests use minimal metadata fixtures, never production robot art."""
import json
import pathlib
import struct
import tempfile
import unittest
from unittest.mock import patch

import prepare_avatar as avatar
import release_gate

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
        self.assertEqual("authored", report["source_kind"])

    def test_invalid_authored_source_never_falls_back_or_overwrites_runtime(self):
        self.write(self.authored, b"invalid export")
        self.write(self.runtime, b"previous asset")
        with patch.object(avatar.subprocess, "run") as generate:
            with self.assertRaises(ValueError):
                avatar.prepare_avatar(self.root, allow_procedural=True)
        generate.assert_not_called()
        self.assertEqual(b"previous asset", self.runtime.read_bytes())

    def test_missing_authored_source_requires_explicit_development_option(self):
        with patch.object(avatar.subprocess, "run") as generate:
            with self.assertRaises(ValueError):
                avatar.prepare_avatar(self.root)
        generate.assert_not_called()
        self.assertFalse(self.runtime.exists())

    def test_development_fallback_is_separate_and_labelled(self):
        development = fixture(avatar.PROCEDURAL_GENERATOR)
        def generate(*args, **kwargs):
            self.write(self.root / avatar.PROCEDURAL_RELATIVE, development)
        with patch.object(avatar.subprocess, "run", side_effect=generate):
            report = avatar.prepare_avatar(self.root, allow_procedural=True)
        self.assertFalse(self.authored.exists())
        self.assertEqual(development, self.runtime.read_bytes())
        self.assertEqual("procedural-development", report["source_kind"])

    def test_procedural_model_cannot_be_relabelled_as_authored(self):
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

    def test_truncated_binary_is_rejected(self):
        with self.assertRaises(ValueError):
            avatar.validate_contract(fixture()[:-1])

if __name__ == "__main__":
    unittest.main()

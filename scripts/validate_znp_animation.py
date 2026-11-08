#!/usr/bin/env python3
"""Validate the exact GORO animated face asset before packaging or RCSP transfer."""
import base64
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app/src/main/assets/goro"
PARTS = [
    "znp_faces_00.b64", "znp_faces_00b.b64",
    *[f"znp_faces_{i:02d}.b64" for i in range(1, 5)],
    "znp_faces_05.b64", "znp_faces_05b.b64",
    *[f"znp_faces_{i:02d}.b64" for i in range(6, 12)],
    "znp_faces_12.b64", "znp_faces_12b.b64",
    *[f"znp_faces_{i:02d}.b64" for i in range(13, 18)],
]

def gif_info(data):
    if len(data) < 14 or data[:6] not in (b"GIF87a", b"GIF89a"):
        raise ValueError("GIF header missing or invalid")
    width = int.from_bytes(data[6:8], "little")
    height = int.from_bytes(data[8:10], "little")
    packed = data[10]
    pos = 13 + (3 * (2 ** ((packed & 7) + 1)) if packed & 0x80 else 0)
    frames = 0
    trailer = False
    while pos < len(data):
        marker = data[pos]
        pos += 1
        if marker == 0x3B:
            trailer = True
            break
        if marker == 0x21:
            if pos >= len(data):
                raise ValueError("truncated GIF extension")
            pos += 1
            while True:
                if pos >= len(data):
                    raise ValueError("truncated GIF extension block")
                size = data[pos]
                pos += 1
                if size == 0:
                    break
                pos += size
        elif marker == 0x2C:
            frames += 1
            if pos + 9 > len(data):
                raise ValueError("truncated GIF image descriptor")
            local_packed = data[pos + 8]
            pos += 9
            if local_packed & 0x80:
                pos += 3 * (2 ** ((local_packed & 7) + 1))
            if pos >= len(data):
                raise ValueError("missing GIF LZW code size")
            pos += 1
            while True:
                if pos >= len(data):
                    raise ValueError("truncated GIF image data")
                size = data[pos]
                pos += 1
                if size == 0:
                    break
                pos += size
        else:
            raise ValueError(f"unexpected GIF block marker 0x{marker:02x}")
        if pos > len(data):
            raise ValueError("GIF block exceeds file length")
    if not trailer or pos != len(data):
        raise ValueError("GIF trailer missing or trailing bytes found")
    return width, height, frames

def main():
    missing = [name for name in PARTS if not (ASSET_DIR / name).is_file()]
    if missing:
        raise SystemExit("Missing GORO animation fragments: " + ", ".join(missing))
    encoded = "".join((ASSET_DIR / name).read_text(encoding="ascii").strip() for name in PARTS)
    try:
        data = base64.b64decode(encoded, validate=True)
        width, height, frames = gif_info(data)
    except Exception as exc:
        raise SystemExit("GORO ZNP animation invalid: " + str(exc))
    if (width, height) != (320, 172):
        raise SystemExit(f"Unexpected ZNP resolution: {width}x{height}; expected 320x172")
    if frames < 5:
        raise SystemExit(f"Only {frames} animation frames; expected at least 5")
    portrait = base64.b64decode((ASSET_DIR / "znp_faces_240x296.b64").read_text().strip(), validate=True)
    pw, ph, pf = gif_info(portrait)
    if (pw, ph) != (240, 296) or pf != frames:
        raise SystemExit("Portrait Z02 animation dimensions or frames differ")
    print(f"GORO Z02 portrait PASS: {pw}x{ph}, {pf} frames, {len(portrait)} bytes")
    print(f"GORO ZNP animation PASS: {width}x{height}, {frames} frames, {len(data)} bytes, {len(PARTS)} fragments")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""GORO 12/12 honest release gate. No green release from a successful debug build."""
import argparse
import json
import pathlib
import os
import subprocess
import struct
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/goro/goro_rigged.glb"
EVIDENCE = ROOT / "evidence"

def glb_report():
    if not ASSET.exists():
        return False, "Falta GLB rigged del robot aprobado."
    try:
        data = ASSET.read_bytes()
        if len(data) < 20 or data[:4] != b"glTF":
            return False, "Archivo no es GLB 2.0."
        version, length = struct.unpack_from("<II", data, 4)
        if version != 2 or length != len(data):
            return False, "GLB inválido o incompleto."
        json_length, chunk_type = struct.unpack_from("<II", data, 12)
        if chunk_type != 0x4E4F534A:
            return False, "Falta bloque JSON."
        gltf = json.loads(data[20:20 + json_length].decode("utf-8"))
        nodes = {n.get("name") for n in gltf.get("nodes", [])}
        bones = {"root","pelvis","spine","chest","neck","head","left_shoulder","left_elbow",
                 "left_wrist","left_hand","right_shoulder","right_elbow","right_wrist","right_hand"}
        if not bones.issubset(nodes):
            return False, "Rig incompleto. Faltan huesos requeridos."
        if not gltf.get("skins"):
            return False, "GLB sin skeleton/skin."
        shapes = set()
        for mesh in gltf.get("meshes", []):
            shapes.update(mesh.get("extras", {}).get("targetNames", []))
        if not {"viseme_A","viseme_E","viseme_I","viseme_O","viseme_U",
                "viseme_MBP","eye_blink_left","eye_blink_right","brow_raise","smile"}.issubset(shapes):
            return False, "GLB sin visemas y expresiones faciales exigidas."
        if not gltf.get("materials") or not gltf.get("textures"):
            return False, "GLB sin materiales y texturas PBR."
        return True, "Estructura GLB y rig declarados; calidad visual y movimiento requieren prueba física."
    except (OSError, ValueError, KeyError, struct.error, UnicodeError) as exc:
        return False, "Error inspeccionando GLB: " + type(exc).__name__

def verified(name, required):
    path = EVIDENCE / name
    if not path.is_file():
        return False, "Falta evidencia física o de servicio: " + name
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
        missing = [key for key in required if doc.get(key) is not True]
        return (not missing,
                "Declaraciones de prueba presentes; requieren auditoría independiente." if not missing
                else "Evidencia incompleta: " + ", ".join(missing))
    except (OSError, ValueError, TypeError):
        return False, "Evidencia JSON inválida: " + name

def stage_checks():
    avatar_ok, avatar_msg = glb_report()
    nvidia_ok, nvidia_msg = verified("nvidia_e2e.json",
                                   ["authenticated_proxy", "nemotron_response", "rate_limit_test"])
    visual_ok, visual_msg = verified("visual_match.json",
                                   ["approved_reference_comparison", "light_dark_professional", "android_capture"])
    ble_ok, ble_msg = verified("znp_hardware.json",
                             ["z02_observed", "n02_observed", "audio_confirmed", "touch_confirmed", "display_verified"])
    android_ok, android_msg = verified("android_physical.json",
                                     ["calendar_sync", "gps_weather", "continuous_voice", "reconnect",
                                      "frame_rate_measured", "memory_measured"])
    apk=ROOT / "app/build/outputs/apk/release/app-release.apk"
    signed=False
    if apk.is_file():
        sdk=pathlib.Path(os.environ.get("ANDROID_HOME", "/usr/local/lib/android/sdk"))
        candidates=sorted(sdk.glob("build-tools/*/apksigner"), reverse=True)
        if candidates:
            result=subprocess.run([str(candidates[0]), "verify", "--verbose", str(apk)],
                                  capture_output=True, text=True, timeout=30)
            signed=result.returncode==0
    return [
      {"step": "04 NVIDIA", "ok": nvidia_ok, "reason": nvidia_msg},
      {"step": "09 3D", "ok": avatar_ok and android_ok, "reason": avatar_msg if not avatar_ok else android_msg},
      {"step": "10 Visual", "ok": visual_ok, "reason": visual_msg},
      {"step": "11 ZNP", "ok": ble_ok, "reason": ble_msg},
      {"step": "12 Pruebas físicas", "ok": android_ok, "reason": android_msg},
      {"step": "12 APK firmada", "ok": signed, "reason": "Firma APK verificada con apksigner." if signed else "APK release inexistente o firma aún no verificada."},
    ]

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--strict", action="store_true", help="Fail build unless ALL release checks pass.")
    p.add_argument("--report", default="build/goro-release-readiness.json")
    args = p.parse_args()
    checks = stage_checks()
    ready = all(x["ok"] for x in checks)
    report = {"generated_at": datetime.now(timezone.utc).isoformat(),
              "release_ready_12_of_12": ready, "checks": checks}
    output = ROOT / args.report
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    if args.strict and not ready:
        raise SystemExit("GORO NO está aprobado 12/12; ver controles anteriores.")

if __name__ == "__main__":
    main()

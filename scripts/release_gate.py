#!/usr/bin/env python3
"""GORO 12/12 honest release gate. No green release from a successful debug build."""
import argparse
import json
import pathlib
import os
import subprocess
from prepare_avatar import AUTHORED_RELATIVE, validate_contract
from sign_owner_apk import SigningError, apk_certificate, check_compatibility, package_info
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/goro/goro_rigged.glb"
AUTHORED_ASSET = ROOT / AUTHORED_RELATIVE
EVIDENCE = ROOT / "evidence"

def glb_report():
    if not AUTHORED_ASSET.is_file():
        return False, "Falta GLB autorado en " + AUTHORED_RELATIVE + "; el procedural es solo de desarrollo."
    if not ASSET.is_file():
        return False, "Falta el GLB empaquetado para Android."
    try:
        data = ASSET.read_bytes()
        if data != AUTHORED_ASSET.read_bytes():
            return False, "El GLB empaquetado no coincide con la fuente autorada."
        validate_contract(data, authored=True)
        return True, "Fuente autorada preservada y contrato GLB verificado; falta aceptación visual y física."
    except (OSError, ValueError, TypeError, KeyError, IndexError, AttributeError) as exc:
        return False, "Error inspeccionando GLB autorado: " + type(exc).__name__

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

def installed_update_report(apk, build_tools):
    """A valid signature alone does not establish an update preserving app data."""
    try:
        evidence = json.loads((EVIDENCE / "signature_compatibility.json").read_text(encoding="utf-8"))
        if evidence.get("installed_device_certificate_verified") is not True:
            return False, "No se verificó el certificado de la app instalada; una firma válida no basta."
        baseline = {"package": evidence["installed_package"],
                    "version_code": evidence["installed_version_code"]}
        if not isinstance(baseline["version_code"], int) or isinstance(baseline["version_code"], bool):
            return False, "Versión instalada inválida en evidencia de firma."
        check_compatibility(package_info(apk, build_tools), baseline,
                            apk_certificate(apk, build_tools), evidence["installed_certificate_sha256"])
        return True, "Paquete, versión y certificado de la APK coinciden con la evidencia del dispositivo."
    except (SigningError, OSError, ValueError, TypeError, KeyError, subprocess.TimeoutExpired):
        return False, "No se acreditó una actualización compatible con la instalación existente."

def stage_checks():
    avatar_ok, avatar_msg = glb_report()
    nvidia_ok, nvidia_msg = verified("nvidia_e2e.json",
                                   ["nvidia_authenticated", "nemotron_response", "rate_limit_test"])
    visual_ok, visual_msg = verified("visual_match.json",
                                   ["approved_reference_comparison", "light_dark_professional", "android_capture"])
    concept_ok, concept_msg = verified("concept_e2e.json",
                                     ["voice_dialogue", "calendar_crud", "gps_weather",
                                      "memory_persistence", "reminders"])
    haptic_ok, haptic_msg = verified("haptics_physical.json",
                                    ["pressed_scale", "ripple_feedback", "haptic_motor"])
    speech_ok, speech_msg = verified("speech_e2e.json",
                                    ["magpie_authenticated", "diego_voice_id", "wav_header_valid",
                                     "playback_device_confirmed"])
    gmail_ok, gmail_msg = verified("gmail_e2e.json",
                                  ["oauth_authorized", "gmail_api_authenticated", "message_list_read"])
    ble_ok, ble_msg = verified("znp_hardware.json",
                             ["n02_observed", "n02_identity_verified", "n02_protocol_verified", "audio_confirmed", "touch_confirmed", "display_verified"])
    android_ok, android_msg = verified("android_physical.json",
                                     ["calendar_sync", "gps_weather", "continuous_voice", "reconnect",
                                      "frame_rate_measured", "memory_measured"])
    apk=ROOT / "app/build/outputs/apk/release/app-release.apk"
    signed=False
    update_ok=False
    update_msg="APK release firmada inexistente; compatibilidad de actualización sin verificar."
    if apk.is_file():
        sdk=pathlib.Path(os.environ.get("ANDROID_HOME", "/usr/local/lib/android/sdk"))
        candidates=sorted(sdk.glob("build-tools/*/apksigner"), reverse=True)
        if candidates:
            result=subprocess.run([str(candidates[0]), "verify", "--verbose", str(apk)],
                                  capture_output=True, text=True, timeout=30)
            signed=result.returncode==0
            if signed:
                update_ok,update_msg=installed_update_report(apk,candidates[0].parent)
    return [
      {"step": "04 NVIDIA", "ok": nvidia_ok, "reason": nvidia_msg},
      {"step": "09 3D", "ok": avatar_ok and android_ok, "reason": avatar_msg if not avatar_ok else android_msg},
      {"step": "10 Visual", "ok": visual_ok, "reason": visual_msg},
      {"step": "10 Tactile", "ok": haptic_ok, "reason": haptic_msg},
      {"step": "05 NVIDIA Speech", "ok": speech_ok, "reason": speech_msg},
      {"step": "06 Gmail API", "ok": gmail_ok, "reason": gmail_msg},
      {"step": "Concept E2E", "ok": concept_ok, "reason": concept_msg},
      {"step": "11 ZNP", "ok": ble_ok, "reason": ble_msg},
      {"step": "12 Pruebas físicas", "ok": android_ok, "reason": android_msg},
      {"step": "12 APK firmada", "ok": signed, "reason": "Firma APK verificada con apksigner." if signed else "APK release inexistente o firma aún no verificada."},
      {"step": "12 Actualización compatible", "ok": update_ok, "reason": update_msg},
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

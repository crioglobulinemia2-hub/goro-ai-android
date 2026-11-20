#!/usr/bin/env python3
"""Sign privately only after proving package, version and key match a baseline APK.

The baseline is an APK supplied by the caller, not proof of what is installed.
No install, uninstall, data reset or signing-key rotation is performed.
Passwords stay in private files and are never command-line values or log output.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


class SigningError(Exception):
    pass


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=120)
    if result.returncode:
        # Tool output may contain paths or private configuration. Do not echo it.
        raise SigningError(Path(command[0]).name + " failed; no output APK was published")
    return result.stdout


def package_info(apk, build_tools):
    output = run([str(build_tools / "aapt"), "dump", "badging", str(apk)])
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", output, re.M)
    if not match:
        raise SigningError("Cannot determine APK package and version")
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise SigningError("APK ZIP integrity failed")
    return {"package": match[1], "version_code": int(match[2])}


def apk_certificate(apk, build_tools):
    output = run([str(build_tools / "apksigner"), "verify", "--print-certs", str(apk)])
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$", output, re.M)
    if len(digests) != 1:
        raise SigningError("Exactly one verified APK signer is required")
    return digests[0].lower()


def check_compatibility(candidate, baseline, key_certificate, baseline_certificate):
    if candidate["package"] != baseline["package"]:
        raise SigningError("Package differs from baseline; a second app is not an update")
    if candidate["version_code"] < baseline["version_code"]:
        raise SigningError("Candidate version is older than baseline")
    if key_certificate != baseline_certificate:
        raise SigningError("Signing key differs from baseline; existing data cannot be preserved by this update")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned-apk", type=Path, required=True)
    parser.add_argument("--baseline-apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--store-password-file", type=Path, required=True)
    parser.add_argument("--key-password-file", type=Path)
    parser.add_argument("--key-alias-file", type=Path, required=True)
    parser.add_argument("--output", type=Path, help="Omit to check compatibility without signing")
    args = parser.parse_args()
    keytool = Path(os.environ.get("JAVA_HOME", "")) / "bin/keytool"
    if not keytool.is_file():
        raise SigningError("JAVA_HOME must point to a JDK containing keytool")
    for file in (args.unsigned_apk, args.baseline_apk, args.keystore,
                 args.store_password_file, args.key_alias_file):
        if not file.is_file():
            raise SigningError("A required input file is unavailable")
    if args.output and args.output.exists():
        raise SigningError("Output already exists; refusing to overwrite it")
    candidate = package_info(args.unsigned_apk, args.build_tools)
    baseline = package_info(args.baseline_apk, args.build_tools)
    baseline_cert = apk_certificate(args.baseline_apk, args.build_tools)
    alias = args.key_alias_file.read_text(encoding="utf-8").strip()
    if not alias:
        raise SigningError("Key alias is empty")
    # Temporary files inherit private permissions and are removed on any failure.
    with tempfile.TemporaryDirectory(prefix="goro-sign-") as temp:
        temp = Path(temp)
        public_cert = temp / "certificate.der"
        run([str(keytool), "-exportcert", "-keystore", str(args.keystore),
             "-storepass:file", str(args.store_password_file), "-alias", alias,
             "-file", str(public_cert)])
        key_cert = hashlib.sha256(public_cert.read_bytes()).hexdigest()
        check_compatibility(candidate, baseline, key_cert, baseline_cert)
        report = {
            "package": candidate["package"],
            "candidate_version_code": candidate["version_code"],
            "baseline_version_code": baseline["version_code"],
            "certificate_sha256": key_cert,
            "package_and_signer_compatible_with_baseline": True,
            "installed_device_certificate_verified": False,
            "signed_output_created": False,
        }
        if args.output:
            aligned = temp / "aligned.apk"
            signed = temp / "signed.apk"
            run([str(args.build_tools / "zipalign"), "-P", "16", "4",
                 str(args.unsigned_apk), str(aligned)])
            sign_command = [str(args.build_tools / "apksigner"), "sign", "--ks", str(args.keystore),
                            "--ks-key-alias", alias, "--ks-pass", "file:" + str(args.store_password_file)]
            if args.key_password_file:
                # apksigner consumes successive lines when a password filename is
                # reused. A separate private copy supports equal password files.
                key_password = temp / "key-password"
                key_password.write_bytes(args.key_password_file.read_bytes())
                key_password.chmod(0o600)
                sign_command += ["--key-pass", "file:" + str(key_password)]
            sign_command += ["--v4-signing-enabled", "false", "--out", str(signed), str(aligned)]
            run(sign_command)
            if apk_certificate(signed, args.build_tools) != baseline_cert:
                raise SigningError("Signed output certificate differs from baseline")
            run([str(args.build_tools / "zipalign"), "-c", "-P", "16", "4", str(signed)])
            if package_info(signed, args.build_tools) != candidate:
                raise SigningError("Signed output package changed")
            # Exclusive create prevents a concurrent invocation replacing a deliverable.
            fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            try:
                with os.fdopen(fd, "wb") as target:
                    target.write(signed.read_bytes())
            except BaseException:
                args.output.unlink(missing_ok=True)
                raise
            report["signed_output_created"] = True
            report["apk_sha256"] = hashlib.sha256(signed.read_bytes()).hexdigest()
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (SigningError, OSError, ValueError, zipfile.BadZipFile, subprocess.TimeoutExpired) as exc:
        # Never print a subprocess command, credential or private file content.
        print(json.dumps({"signed_output_created": False,
                          "error": str(exc) if isinstance(exc, SigningError) else type(exc).__name__}))
        raise SystemExit(2)

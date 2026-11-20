#!/usr/bin/env bash
# CI-only diagnostic fixture, not the user's original app or its data.
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BT="$SDK/build-tools/36.0.0"
OUT=build/parallel-install
mkdir -p "$OUT"
PREVIEW=app/build/outputs/apk/debug/app-debug.apk
test -f "$PREVIEW"
"$BT/aapt2" dump badging "$PREVIEW" > "$OUT/preview-badging.txt"
"$BT/aapt2" dump xmltree "$PREVIEW" --file AndroidManifest.xml > "$OUT/preview-manifest.txt"
grep -q "name='ar.goro.ai.preview'" "$OUT/preview-badging.txt"
grep -q 'ar.goro.ai.preview.androidx-startup' "$OUT/preview-manifest.txt"
grep -q 'ar.goro.ai.preview.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' "$OUT/preview-manifest.txt"
if grep -q 'ar.goro.ai.personal' "$OUT/preview-manifest.txt"; then
  echo 'Preview manifest retains personal-package identifier'; exit 1
fi
# Refuse to replace any pre-existing personal package, even on CI.
if adb shell pm path ar.goro.ai.personal | grep -q '^package:'; then
  echo 'Personal package already exists; fixture installation refused'; exit 1
fi
cat > "$OUT/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ar.goro.ai.personal" android:versionCode="1" android:versionName="fixture">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
  <permission android:name="ar.goro.ai.personal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="signature"/>
  <application android:hasCode="false" android:debuggable="true" android:label="GORO coexistence fixture">
    <provider android:name="android.content.ContentProvider" android:authorities="ar.goro.ai.personal.androidx-startup" android:exported="false"/>
  </application>
</manifest>
XML
"$BT/aapt2" link -I "$SDK/platforms/android-36/android.jar" --manifest "$OUT/AndroidManifest.xml" -o "$OUT/fixture-unsigned.apk"
keytool -genkeypair -keystore "$OUT/fixture.p12" -storepass fixture-test-only -keypass fixture-test-only -alias fixture -dname 'CN=CI coexistence fixture' -keyalg RSA -keysize 2048 -validity 2 -storetype PKCS12 >/dev/null 2>&1
"$BT/apksigner" sign --ks "$OUT/fixture.p12" --ks-pass pass:fixture-test-only --out "$OUT/fixture.apk" "$OUT/fixture-unsigned.apk"
"$BT/apksigner" verify --print-certs "$OUT/fixture.apk" > "$OUT/fixture-cert.txt"
"$BT/apksigner" verify --print-certs "$PREVIEW" > "$OUT/preview-cert.txt"
FIXTURE_CERT=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT/fixture-cert.txt")
PREVIEW_CERT=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT/preview-cert.txt")
test -n "$FIXTURE_CERT"
test -n "$PREVIEW_CERT"
test "$FIXTURE_CERT" != "$PREVIEW_CERT"
adb install "$OUT/fixture.apk" > "$OUT/fixture-install.txt"
adb shell "run-as ar.goro.ai.personal sh -c 'mkdir -p files; echo goro-ci-preserved > files/coexistence-sentinel'"
adb install -r "$PREVIEW" > "$OUT/preview-install.txt"
adb shell pm path ar.goro.ai.personal > "$OUT/personal-path.txt"
adb shell pm path ar.goro.ai.preview > "$OUT/preview-path.txt"
grep -q '^package:' "$OUT/personal-path.txt"
grep -q '^package:' "$OUT/preview-path.txt"
SENTINEL=$(adb shell run-as ar.goro.ai.personal cat files/coexistence-sentinel | tr -d '\r')
test "$SENTINEL" = goro-ci-preserved
cat > "$OUT/result.json" <<'JSON'
{"fixture_only":true,"different_certificates":true,"both_packages_installed":true,"fixture_private_data_preserved":true,"real_installed_app_tested":false}
JSON
echo 'Parallel installation PASS: different certificates, distinct authority/permission, fixture data preserved.'

#!/usr/bin/env bash
set -uo pipefail
mkdir -p build
# Read the real Android identifiers from the project instead of duplicating them.
PACKAGE_NAME="$(sed -n "s/^[[:space:]]*applicationId ['\"]\([^'\"]*\)['\"].*/\1/p" app/build.gradle | head -n 1)"
APP_NAMESPACE="$(sed -n 's/^package[[:space:]]\+\([^;]*\);/\1/p' app/src/main/java/ar/goro/ai/MainActivity.java | head -n 1)"
APP_ACTIVITY="$APP_NAMESPACE.MainActivity"
if [ -z "$PACKAGE_NAME" ] || [ -z "$APP_NAMESPACE" ]; then
  echo "Unable to resolve applicationId or MainActivity package from the Android project."
  exit 1
fi
adb logcat -c >/dev/null 2>&1 || true
adb logcat -v threadtime > build/goro-emulator-logcat.txt 2>&1 &
LOG_PID=$!
set +e
gradle --no-daemon connectedDebugAndroidTest
RESULT=$?
# Instrumentation copies the before/after render evidence to shared emulator
# storage before its app can be uninstalled by the test runner.
adb pull /sdcard/Download/goro-visual-test build/goro-visual-test \
  > build/goro-visual-evidence-pull.txt 2>&1 || true
# Android instrumentation may uninstall the app under test. Reinstall the
# exact same freshly built APK before collecting UI evidence.
if adb get-state >/dev/null 2>&1; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk     > build/goro-emulator-install.txt 2>&1
  INSTALL_STATUS=$?
  if [ "$INSTALL_STATUS" -eq 0 ]; then
    # Reinstallation after instrumentation clears its permission grants.
    # Grant only permissions declared by this test app so UI evidence shows
    # the actual screen instead of an Android runtime permission dialog.
    for permission in BLUETOOTH_SCAN BLUETOOTH_CONNECT RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
      adb shell pm grant "$PACKAGE_NAME" "android.permission.$permission" >/dev/null 2>&1 || true
    done
    adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
    adb shell pm path "$PACKAGE_NAME" > build/goro-emulator-package-path.txt 2>&1
    if ! grep -q '^package:' build/goro-emulator-package-path.txt; then
      echo "Installed package $PACKAGE_NAME was not found on the emulator." >> build/goro-emulator-launch.txt
      cat build/goro-emulator-package-path.txt >> build/goro-emulator-launch.txt
      START_STATUS=1
    else
      adb shell am start -W -n "$PACKAGE_NAME/$APP_ACTIVITY" > build/goro-emulator-launch.txt 2>&1
      START_STATUS=$?
    fi
    sleep 6
    adb shell dumpsys activity activities > build/goro-emulator-activities.txt 2>&1 || true
    if ! grep -Fq "$APP_ACTIVITY" build/goro-emulator-activities.txt; then
      echo "GORO MainActivity not visible after explicit launch; retrying launcher entry." >> build/goro-emulator-launch.txt
      adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >> build/goro-emulator-launch.txt 2>&1 || true
      sleep 3
      adb shell dumpsys activity activities > build/goro-emulator-activities.txt 2>&1 || true
    fi
    if grep -Fq "$APP_ACTIVITY" build/goro-emulator-activities.txt; then
      START_STATUS=0
    else
      echo "GORO MainActivity is not the resumed activity after retry." >> build/goro-emulator-launch.txt
      START_STATUS=1
    fi
    adb exec-out screencap -p > build/goro-emulator-ui.png 2>/dev/null || true
    test -s build/goro-emulator-ui.png || rm -f build/goro-emulator-ui.png
    adb shell dumpsys gfxinfo "$PACKAGE_NAME"       > build/goro-emulator-gfxinfo.txt 2>&1 || true
    adb shell dumpsys activity activities       > build/goro-emulator-activities.txt 2>&1 || true
    # Capture the same APK at Tab A8-like landscape geometry as well.
    adb shell wm size 1920x1200 >/dev/null 2>&1
    adb shell wm density 240 >/dev/null 2>&1
    adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1
    adb shell am start -W -n "$PACKAGE_NAME/$APP_ACTIVITY" > build/goro-emulator-tablet-launch.txt 2>&1
    sleep 4
    adb shell dumpsys activity activities > build/goro-emulator-tablet-activities.txt 2>&1 || true
    if ! grep -Fq "$APP_ACTIVITY" build/goro-emulator-tablet-activities.txt; then
      echo "GORO MainActivity is not visible at tablet geometry." >> build/goro-emulator-tablet-launch.txt
      RESULT=1
    fi
    adb exec-out screencap -p > build/goro-emulator-tablet.png 2>/dev/null || true
    test -s build/goro-emulator-tablet.png || rm -f build/goro-emulator-tablet.png
    adb shell wm size reset >/dev/null 2>&1
    adb shell wm density reset >/dev/null 2>&1
    if [ "$START_STATUS" -ne 0 ]; then
      echo "GORO launch command failed after reinstall."
      RESULT=1
    fi
  else
    echo "GORO APK reinstall failed after instrumentation."
    cat build/goro-emulator-install.txt
    RESULT=1
  fi
fi
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
if [ "$RESULT" -ne 0 ]; then
  echo "GORO Android instrumentation or launch failed. Device diagnostics:"
  grep -E -A 40 -B 4 'FATAL EXCEPTION|Fatal signal|AndroidRuntime|JNI DETECTED ERROR|Abort message|Caused by:'     build/goro-emulator-logcat.txt | tail -n 400 || true
fi
exit "$RESULT"

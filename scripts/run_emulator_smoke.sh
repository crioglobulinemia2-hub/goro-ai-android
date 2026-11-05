#!/usr/bin/env bash
set -uo pipefail
mkdir -p build
PACKAGE_NAME=ar.goro.ai.preview
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
    adb shell am start -W -n "$PACKAGE_NAME"/ar.goro.ai.MainActivity       > build/goro-emulator-launch.txt 2>&1
    START_STATUS=$?
    sleep 6
    adb exec-out screencap -p > build/goro-emulator-ui.png 2>/dev/null || true
    test -s build/goro-emulator-ui.png || rm -f build/goro-emulator-ui.png
    adb shell dumpsys gfxinfo "$PACKAGE_NAME"       > build/goro-emulator-gfxinfo.txt 2>&1 || true
    adb shell dumpsys activity activities       > build/goro-emulator-activities.txt 2>&1 || true
    # Capture the same APK at Tab A8-like landscape geometry as well.
    adb shell wm size 1920x1200 >/dev/null 2>&1
    adb shell wm density 240 >/dev/null 2>&1
    adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1
    adb shell am start -W -n "$PACKAGE_NAME"/ar.goro.ai.MainActivity > build/goro-emulator-tablet-launch.txt 2>&1
    sleep 4
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
if [ "$RESULT" -eq 0 ] && [ "${GITHUB_ACTIONS:-}" = true ]; then
  bash scripts/test_parallel_install.sh || RESULT=1
fi
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
if [ "$RESULT" -ne 0 ]; then
  echo "GORO Android instrumentation or launch failed. Device diagnostics:"
  grep -E -A 40 -B 4 'FATAL EXCEPTION|Fatal signal|AndroidRuntime|JNI DETECTED ERROR|Abort message|Caused by:'     build/goro-emulator-logcat.txt | tail -n 400 || true
fi
exit "$RESULT"

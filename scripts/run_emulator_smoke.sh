#!/usr/bin/env bash
set -uo pipefail
mkdir -p build
adb logcat -c >/dev/null 2>&1 || true
adb logcat -v threadtime > build/goro-emulator-logcat.txt 2>&1 &
LOG_PID=$!
set +e
gradle --no-daemon connectedDebugAndroidTest
RESULT=$?
# Android instrumentation may uninstall the app under test. Reinstall the
# exact same freshly built APK before collecting UI evidence.
if adb get-state >/dev/null 2>&1; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk     > build/goro-emulator-install.txt 2>&1
  INSTALL_STATUS=$?
  if [ "$INSTALL_STATUS" -eq 0 ]; then
    adb shell am force-stop ar.goro.ai.personal >/dev/null 2>&1 || true
    adb shell am start -W -n ar.goro.ai.personal/ar.goro.ai.MainActivity       > build/goro-emulator-launch.txt 2>&1
    START_STATUS=$?
    sleep 6
    adb exec-out screencap -p > build/goro-emulator-ui.png 2>/dev/null || true
    test -s build/goro-emulator-ui.png || rm -f build/goro-emulator-ui.png
    adb shell dumpsys gfxinfo ar.goro.ai.personal       > build/goro-emulator-gfxinfo.txt 2>&1 || true
    adb shell dumpsys activity activities       > build/goro-emulator-activities.txt 2>&1 || true
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

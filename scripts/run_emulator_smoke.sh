#!/usr/bin/env bash
set -uo pipefail
mkdir -p build
# Keep the Android fatal stack trace even if Android instrumentation aborts.
adb logcat -c >/dev/null 2>&1 || true
adb logcat -v threadtime > build/goro-emulator-logcat.txt 2>&1 &
LOG_PID=$!
set +e
gradle --no-daemon connectedDebugAndroidTest
RESULT=$?
# Capture the REAL app running in an Android emulator for visual review.
# This is evidence of the emulator only, not a physical ZN02 or tablet test.
if adb get-state >/dev/null 2>&1; then
  adb shell am force-stop ar.goro.ai.personal >/dev/null 2>&1 || true
  adb shell am start -n ar.goro.ai.personal/ar.goro.ai.MainActivity >/dev/null 2>&1 || true
  sleep 6
  adb exec-out screencap -p > build/goro-emulator-ui.png 2>/dev/null || true
  test -s build/goro-emulator-ui.png || rm -f build/goro-emulator-ui.png
  adb shell dumpsys gfxinfo ar.goro.ai.personal > build/goro-emulator-gfxinfo.txt 2>&1 || true
fi
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
if [ "$RESULT" -ne 0 ]; then
  echo "GORO Android instrumentation failed; fatal device trace:"
  grep -E -A 40 -B 4 'FATAL EXCEPTION|Fatal signal|AndroidRuntime|JNI DETECTED ERROR|Abort message|Caused by:' build/goro-emulator-logcat.txt | tail -n 500 || true
fi
exit "$RESULT"

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
kill "$LOG_PID" >/dev/null 2>&1 || true
wait "$LOG_PID" >/dev/null 2>&1 || true
if [ "$RESULT" -ne 0 ]; then
  echo "GORO Android instrumentation failed; fatal device trace:"
  grep -E -A 40 -B 4 'FATAL EXCEPTION|Fatal signal|AndroidRuntime|JNI DETECTED ERROR|Abort message|Caused by:' build/goro-emulator-logcat.txt | tail -n 500 || true
fi
exit "$RESULT"

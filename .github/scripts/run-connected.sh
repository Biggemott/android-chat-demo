#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Select exactly one emulator}"
: "${EXPECTED_ANDROID_API:?Set the expected Android API}"

# Never reuse XML, even after a build or install failure.
results=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/connected-XXXXXXXX")
echo "Connected test evidence: $results"
actual_api=$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$actual_api" != "$EXPECTED_ANDROID_API" ]]; then
    echo "Device $ANDROID_SERIAL reports API $actual_api, expected $EXPECTED_ANDROID_API" >&2
    exit 1
fi

bash ./gradlew :app:connectedDebugAndroidTest -PisolatedInstrumentedTests \
    "-PconnectedResultsDir=$results" --console=plain --stacktrace 2>&1 | tee "$results/gradle.log"
python3 .github/scripts/validate_connected.py "$results" "$ANDROID_SERIAL" 2>&1 | tee "$results/validation.log"

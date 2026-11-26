#!/usr/bin/env bash
set -euo pipefail

{
    for tool in sdkmanager avdmanager; do
        executable=$(readlink -f "$(command -v "$tool")")
        echo "$tool: $executable"
        test "$executable" = "$(readlink -f "$ANDROID_HOME/cmdline-tools/22.0/bin/$tool")"
    done
    sdkmanager --version

    if [[ "$EXPECTED_ANDROID_API" == 37 ]]; then
        metadata="$ANDROID_AVD_HOME/test.ini"
        config="$ANDROID_AVD_HOME/test.avd/config.ini"
        grep '^target=' "$metadata"
        grep -E '^(image\.sysdir\.1|hw\.device\.name|hw\.cpu\.ncore|hw\.ramSize)[[:space:]]*=' "$config"
        grep -qx 'target=android-37.2' "$metadata"
        grep -Eq '^image\.sysdir\.1[[:space:]]*=[[:space:]]*system-images/android-37\.2/google_apis_ps16k/x86_64/?$' "$config"
        grep -qx 'hw.device.name=pixel_6' "$config"
        test "$(grep -E '^hw\.cpu\.ncore[[:space:]]*=' "$config" | tail -n 1)" = 'hw.cpu.ncore=2'
        test "$(grep -E '^hw\.ramSize[[:space:]]*=' "$config" | tail -n 1)" = 'hw.ramSize=2560M'
    fi
} 2>&1 | tee "$RUNNER_TEMP/emulator-config.txt"

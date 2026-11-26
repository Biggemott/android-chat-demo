#!/usr/bin/env bash
set -euo pipefail

# The Ubuntu image installs the SDK here even when the job shell lacks its environment.
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}
tools="$sdk_root/cmdline-tools/latest/bin"
sdkmanager="$tools/sdkmanager"
for tool in "$sdkmanager" "$tools/avdmanager" "$sdk_root/platform-tools/adb"; do
    if [[ ! -x "$tool" ]]; then
        echo "Expected executable Android SDK tool at $tool (runner image: ${ImageVersion:-unknown})" >&2
        exit 1
    fi
done

export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"
packages=()
[[ -f "$sdk_root/platforms/android-37.1/android.jar" ]] || packages+=('platforms;android-37.1')
[[ -x "$sdk_root/build-tools/36.0.0/aapt2" ]] || packages+=('build-tools;36.0.0')
if (( ${#packages[@]} )); then
    "$sdkmanager" --sdk_root="$sdk_root" --install "${packages[@]}"
fi
test -f "$sdk_root/platforms/android-37.1/android.jar"
test -x "$sdk_root/build-tools/36.0.0/aapt2"

printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
printf '%s\n' "$sdk_root/platform-tools" "$tools" >> "$GITHUB_PATH"
echo "Using Android SDK at $sdk_root, SDK Platform 37.1 and Build Tools 36.0.0"
"$sdkmanager" --version

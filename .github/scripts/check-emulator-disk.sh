#!/usr/bin/env bash
set -euo pipefail

# Reserve room on the AVD volume for the default userdata partition plus build output.
avd_dir=${1:?Pass the AVD directory}
minimum_bytes=$((12 * 1024 * 1024 * 1024))
df -h "$avd_dir" "$GITHUB_WORKSPACE" "$HOME"
available_bytes=$(df -B1 --output=avail "$avd_dir" | tail -n 1)
if (( available_bytes < minimum_bytes )); then
    echo "API 37 needs 12 GiB free on the AVD volume before launch. Available: ${available_bytes// /} bytes at $avd_dir." >&2
    exit 1
fi

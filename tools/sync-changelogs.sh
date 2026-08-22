#!/usr/bin/env bash
# Mirror a release's changelog onto the per-ABI version codes.
#
# Every published APK is ABI-specific, and each one sits in its own decamillion range above the
# base version code (see the productFlavors block in app/build.gradle.kts). F-Droid and IzzyOnDroid
# look up changelogs by version code, so without these copies no installed build has release notes.
# The base version code keeps its own changelog as the source these are mirrored from.
#
# Run after writing changelogs/<versionCode>.txt for a new release.

set -euo pipefail

cd "$(dirname "$0")/.."

changelogs="fastlane/metadata/android/en-US/changelogs"
base=$(grep -oP 'baseVersionCode\s*=.*?\?:\s*\K\d+' app/build.gradle.kts)
abi_count=$(grep -oP '^val abis = listOf\(\K[^)]+' app/build.gradle.kts | tr -cd ',' | wc -c)
abi_count=$((abi_count + 1))

source_file="$changelogs/$base.txt"
if [ ! -f "$source_file" ]; then
  echo "No $source_file to mirror. Write it first." >&2
  exit 1
fi

for i in $(seq 1 "$abi_count"); do
  target="$changelogs/$((base + i * 10000000)).txt"
  cp "$source_file" "$target"
  echo "wrote $target"
done

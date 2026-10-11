#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Fetch to a scoped cache, verify release bytes, and run without a global install.
version=2.46.0
checksum=4d9e34b62172d645eed6457cac13fc222569974098ef4ee9c3368bedf0196806
cache=".tools/xcodegen-$version"
if [[ ! -x "$cache/xcodegen/bin/xcodegen" ]]; then
  mkdir -p "$cache"
  curl --fail --location --retry 3 "https://github.com/yonaskolb/XcodeGen/releases/download/$version/xcodegen.zip" -o "$cache/download.zip"
  echo "$checksum  $cache/download.zip" | shasum -a 256 -c -
  unzip -q -o "$cache/download.zip" -d "$cache"
fi
"$cache/xcodegen/bin/xcodegen" --version
"$cache/xcodegen/bin/xcodegen" generate --spec project.yml

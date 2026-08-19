#!/usr/bin/env bash
# Builds the shared network layer into an XCFramework and drops it where the
# Swift package expects it.
#
# Requires macOS with Xcode: Kotlin/Native cannot produce Apple binaries on Linux.
set -euo pipefail

CONFIGURATION="${1:-release}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$CONFIGURATION" in
  debug)   TASK="assembleKmpLabNetworkDebugXCFramework" ;;
  release) TASK="assembleKmpLabNetworkReleaseXCFramework" ;;
  *) echo "usage: $(basename "$0") [debug|release]" >&2; exit 2 ;;
esac

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: XCFrameworks can only be produced on macOS with Xcode installed." >&2
  exit 1
fi

echo "==> ./gradlew $TASK"
"$ROOT/gradlew" -p "$ROOT" "$TASK"

BUILT="$ROOT/shared/network/build/XCFrameworks/$CONFIGURATION/KmpLabNetwork.xcframework"
DEST="$ROOT/ios/sdk/Artifacts"

[[ -d "$BUILT" ]] || { echo "error: expected $BUILT" >&2; exit 1; }

rm -rf "${DEST:?}/KmpLabNetwork.xcframework"
mkdir -p "$DEST"
cp -R "$BUILT" "$DEST/"

echo "==> $DEST/KmpLabNetwork.xcframework"

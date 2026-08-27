#!/usr/bin/env bash
#
# Preflight checks for a KmpLab working copy.
#
# Deliberately plain shell with no dependencies beyond coreutils: this is the one
# thing that has to run on a machine where the toolchain is wrong or missing,
# which rules out anything needing a JVM, Gradle or Kotlin.
#
# Exit codes: 0 all good, 1 something is broken, 2 only warnings.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
ROOT="$PWD"

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    RED=$'\033[31m'; YELLOW=$'\033[33m'; GREEN=$'\033[32m'; DIM=$'\033[2m'; OFF=$'\033[0m'
else
    RED=''; YELLOW=''; GREEN=''; DIM=''; OFF=''
fi

FAILURES=0
WARNINGS=0

ok()   { printf '  %sok%s    %s\n' "$GREEN" "$OFF" "$1"; }
warn() { printf '  %swarn%s  %s\n' "$YELLOW" "$OFF" "$1"; WARNINGS=$((WARNINGS + 1)); }
fail() { printf '  %sFAIL%s  %s\n' "$RED" "$OFF" "$1"; FAILURES=$((FAILURES + 1)); }
fix()  { printf '        %s%s%s\n' "$DIM" "$1" "$OFF"; }
section() { printf '\n%s\n' "$1"; }

# Pins currently live in mise.toml's [tools] table. Parsed rather than duplicated
# so there is one place to bump a version, whatever ends up reading it.
pin() {
    awk -F' *= *' -v key="$1" '
        /^\[tools\]/ { inside = 1; next }
        /^\[/        { inside = 0 }
        inside && $1 == key { gsub(/"/, "", $2); print $2; exit }
    ' "$ROOT/mise.toml" 2>/dev/null
}

section "Toolchain"

JAVA_PIN="$(pin java)"                       # e.g. temurin-21
JAVA_WANT="${JAVA_PIN##*-}"                  # e.g. 21
if command -v java >/dev/null 2>&1; then
    JAVA_HAVE="$(java -version 2>&1 | head -1 | sed -n 's/.*[版version"]*[" ]\([0-9][0-9]*\)\..*/\1/p')"
    [ -z "$JAVA_HAVE" ] && JAVA_HAVE="$(java -version 2>&1 | head -1 | sed -n 's/.*"\([0-9][0-9]*\).*/\1/p')"
    if [ -z "$JAVA_WANT" ] || [ "$JAVA_HAVE" = "$JAVA_WANT" ]; then
        ok "JDK $JAVA_HAVE"
    else
        fail "JDK $JAVA_HAVE, but this project wants $JAVA_WANT"
        fix "AGP and Kotlin are not validated against newer JDKs; the Android toolchain rejects them."
    fi
else
    fail "no java on PATH"
    fix "Install JDK ${JAVA_WANT:-21}."
fi

if [ -x "$ROOT/gradlew" ]; then
    ok "Gradle wrapper"
else
    fail "gradlew missing or not executable"
    fix "chmod +x gradlew"
fi

section "Android"

ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$ANDROID_SDK" ] && [ -f "$ROOT/local.properties" ]; then
    ANDROID_SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -1)"
fi

if [ -n "$ANDROID_SDK" ] && [ -d "$ANDROID_SDK" ]; then
    ok "Android SDK at $ANDROID_SDK"
elif [ -n "$ANDROID_SDK" ]; then
    fail "Android SDK path does not exist: $ANDROID_SDK"
else
    fail "Android SDK location unknown"
    if [ -d "$HOME/Library/Android/sdk" ]; then
        fix "echo \"sdk.dir=\$HOME/Library/Android/sdk\" > local.properties"
    else
        fix "Install the SDK via Android Studio, then set ANDROID_HOME or sdk.dir in local.properties."
    fi
fi

if [ "$(uname -s)" != "Darwin" ]; then
    section "iOS"
    warn "not macOS — the iOS tasks are unavailable here, the Android ones are fine"
    printf '\n'
    [ "$FAILURES" -gt 0 ] && exit 1
    exit 0
fi

section "iOS"

if ! command -v xcodebuild >/dev/null 2>&1; then
    fail "xcodebuild not found"
    fix "Install Xcode from the App Store."
else
    DEVELOPER_DIR_PATH="$(xcode-select -p 2>/dev/null)"
    case "$DEVELOPER_DIR_PATH" in
        *CommandLineTools*)
            fail "xcode-select points at the Command Line Tools, not a full Xcode"
            fix "sudo xcode-select -s /Applications/Xcode.app"
            ;;
        "")
            fail "no active developer directory"
            fix "sudo xcode-select -s /Applications/Xcode.app"
            ;;
        *)
            ok "Xcode $(xcodebuild -version 2>/dev/null | head -1 | awk '{print $2}') at $DEVELOPER_DIR_PATH"
            ;;
    esac
fi

TUIST_PIN="$(pin tuist)"
if command -v tuist >/dev/null 2>&1; then
    TUIST_HAVE="$(tuist version 2>/dev/null | tr -d '[:space:]')"
    if [ -z "$TUIST_PIN" ] || [ "$TUIST_HAVE" = "$TUIST_PIN" ]; then
        ok "Tuist $TUIST_HAVE"
    else
        warn "Tuist $TUIST_HAVE, pinned at $TUIST_PIN"
        fix "The manifest API moves between versions; generation may differ."
    fi
else
    fail "tuist not found"
    fix "brew install --cask tuist"
fi

if command -v xcrun >/dev/null 2>&1; then
    SIMULATORS="$(xcrun simctl list devices available 2>/dev/null | grep -c 'iPhone')"
    if [ "${SIMULATORS:-0}" -gt 0 ]; then
        ok "$SIMULATORS iPhone simulator(s) available"
    else
        warn "no iPhone simulators installed"
        fix "Xcode → Settings → Components → iOS Simulator"
    fi
fi

section "Working copy"

XCFRAMEWORK="$ROOT/shared/network/build/XCFrameworks/KmpLabNetwork.xcframework"
if [ -e "$XCFRAMEWORK" ]; then
    ok "KmpLabNetwork.xcframework → $(readlink "$XCFRAMEWORK" 2>/dev/null || echo 'present')"
else
    warn "the Kotlin XCFramework has not been built"
    fix "./gradlew :shared:network:linkKmpLabNetworkDebugXCFramework"
fi

if [ -d "$ROOT/ios/KmpLab.xcworkspace" ]; then
    ok "Xcode workspace generated"
else
    warn "no Xcode workspace yet — it is generated, not committed"
    fix "./gradlew generateXcodeWorkspace"
fi

printf '\n'
if [ "$FAILURES" -gt 0 ]; then
    printf '%s%d problem(s)%s, %d warning(s)\n' "$RED" "$FAILURES" "$OFF" "$WARNINGS"
    exit 1
elif [ "$WARNINGS" -gt 0 ]; then
    printf '%sReady%s, with %d warning(s)\n' "$GREEN" "$OFF" "$WARNINGS"
    exit 2
else
    printf '%sReady.%s\n' "$GREEN" "$OFF"
    exit 0
fi

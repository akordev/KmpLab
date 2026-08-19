# iOS

`sdk/` is a Swift package wrapping `KmpLabNetwork.xcframework`, which
`:shared:network` produces. `sample/` is an app that integrates it.

Both are scaffold only — there is no implementation yet, and none of the Swift
here has been compiled. It was written on Linux, which has no Swift iOS SDK.

## Building

Requires macOS with Xcode, plus [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`).

```bash
./ios/build-xcframework.sh release   # Gradle -> KmpLabNetwork.xcframework
cd ios/sample
xcodegen generate                    # project.yml -> KmpLabSample.xcodeproj
open KmpLabSample.xcodeproj
```

The XCFramework lands in `sdk/Artifacts/` and is not checked in — it is a binary
build output. `Package.swift` declares it as a `binaryTarget`, so the package
will not resolve until the script has run once.

The Xcode project is generated rather than committed: a `.pbxproj` is
machine-written and not reviewable.

## SKIE

The framework is built with [SKIE](https://skie.touchlab.co), which rewrites the
generated Swift API so Kotlin sealed classes arrive as real Swift enums (switched
exhaustively via `onEnum(of:)`, with the generic type argument intact), Kotlin
default arguments become Swift overloads, and Flows become `AsyncSequence`.
Without it the Swift side has to cast through Objective-C, which erases all of
that.

`produceDistributableFramework()` is set because the XCFramework is consumed by
SwiftPM, potentially from a different Xcode version than built it. The framework
is dynamic rather than static for the same reason.

**SKIE has never actually run here.** It is a *linker* plugin, and linking is
skipped on Linux. What is confirmed is only that SKIE 0.10.14 applies cleanly and
supports Kotlin 2.4.10 — its plugin jar bundles a KGP shim for 2.4.0 and lists
2.4.10 among supported versions.

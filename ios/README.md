# iOS

`sdk/` is a static framework wrapping `KmpLabNetwork.xcframework`, which
`:shared:network` produces. `sample/` is an app that integrates it. Consumers get
the built framework as a binary — there is no package manager involved.

Both are scaffold only — there is no implementation yet, and none of the Swift
here has been compiled. It was written on Linux, which has no Swift iOS SDK.

## Building

Requires macOS with Xcode, plus [Tuist](https://tuist.dev) (`brew install tuist`).

```bash
cd ios
tuist generate      # Workspace.swift + Project.swift -> KmpLab.xcworkspace
```

That opens the workspace. Building the sample runs a pre-action that calls
`build-xcframework.sh debug`, so ⌘R always builds current Kotlin and the app can
never run against a stale framework.

For a framework to hand to consumers, run it directly:

```bash
./ios/build-xcframework.sh release
```

Either way the XCFramework lands in `sdk/Artifacts/`, which is not checked in —
it is a binary build output, and `sdk/Project.swift` links it with
`.xcframework(path:)`. Nothing will build until it exists.

Xcode projects are generated rather than committed: a `.pbxproj` is
machine-written, merge-hostile and not reviewable.

## Linking

Everything is static. The Kotlin framework is built with `isStatic = true`, and
`ios/sdk` is a `.staticFramework`, so it all folds into the app binary at link
time.

That combination is why SKIE's `produceDistributableFramework()` matters here: it
turns on Swift library evolution, which XCFrameworks require, along with
`noClangModuleBreadcrumbsInStaticFrameworks`, which exists for precisely this
setup. Known wart — SKIE emits a `swift-module-cache` for static XCFrameworks
that can produce warnings downstream.

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

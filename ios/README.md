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

```
./gradlew :shared:network:assembleKmpLabNetworkDebugXCFramework
```

so ⌘R always builds current Kotlin and the app can never run against a stale
framework. The target sets `ENABLE_USER_SCRIPT_SANDBOXING = NO`, because Gradle
writes outside the build directory and Xcode's script sandbox forbids that.

You rarely need to run this by hand: the `KmpLabNetwork` foreign build target
invokes it, picking Debug or Release to match Xcode's configuration.

The XCFramework is an intermediate, not a deliverable — `KmpLabSDK` is the only
thing consumers ever integrate, and it absorbs the Kotlin framework. So it stays
in Gradle's build directory, at
`shared/network/build/XCFrameworks/<config>/KmpLabNetwork.xcframework`.

A pbxproj cannot vary an xcframework reference by configuration, so
`sdk/Project.swift` instead links a fixed sibling path,
`shared/network/build/XCFrameworks/KmpLabNetwork.xcframework`, which is a symlink
each assemble task repoints at the build type it just produced. Nothing will
build until that symlink exists — run `mise run setup` on a fresh clone.

One gotcha: Xcode's Run Script environment is not your shell, so `gradlew` may not
find a JDK. If the pre-action fails with a Java error, set `JAVA_HOME` explicitly
in the script.

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

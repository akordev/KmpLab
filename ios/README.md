# iOS

`sdk/` is the public Swift surface, a framework wrapping
`KmpLabNetwork.xcframework` from `:shared:network`. `sample/` is an app that
integrates it the way a third party would. Consumers get the built framework as
a binary; no package manager is involved.

The public surface is still empty — `KmpLabSDK.swift` is a placeholder.
`ApiResultShowcase.swift` is not part of it: it touches Kotlin types directly,
and exists to keep the generated interop shape visible and compiling.

## Building

From the repository root:

```bash
./gradlew buildIosSampleDebug     # or buildIosSdkDebug, …Release
```

That builds the Kotlin XCFramework, generates the Xcode workspace if a manifest
changed, and runs `xcodebuild`. There is no separate setup step, and nothing to
run in a particular order on a fresh clone. `./scripts/doctor.sh` reports a
missing Xcode, Tuist or simulator before any of it fails obscurely.

To work in Xcode, open `KmpLab.xcworkspace` once a task has generated it.

## Generated projects, not committed ones

Tuist generates the workspace and both `.xcodeproj`s from the `Project.swift`
manifests. A `.pbxproj` is machine-written, merge-hostile and not reviewable, so
none of it is in git.

Regeneration is rarer than it looks. The targets use **buildable folders**, which
Tuist binds to Xcode's synchronized groups: the project references the `Sources`
directory itself rather than a file list frozen at generation time, so adding or
renaming a Swift file needs no regeneration at all. Only a manifest change does,
and the `generateXcodeWorkspace` Gradle task notices that for you.

`Tuist.swift` is what roots Tuist at `ios/`. Without it, Tuist walks up to the
nearest ancestor holding a `.git` directory and expects its helpers at the
repository root. `Tuist/ProjectDescriptionHelpers/` holds settings shared by both
projects; that path is fixed, because Tuist compiles it into a Swift module of
exactly that name — which is what `import ProjectDescriptionHelpers` resolves to.

The notable setting there is `SWIFT_VERSION = 6.0`. Tuist defaults to 5, which
downgrades data-race violations to warnings. The SDK ships as a binary, so what
is `Sendable` and what is actor-isolated is part of its public contract — adding
`Sendable` later is additive, adding isolation later breaks every consumer.

## How the Kotlin framework arrives

`sdk/Project.swift` declares a `foreignBuild` target: Tuist models the Gradle
invocation as a real node in the dependency graph rather than a script phase
attached to whichever target happened to need it. That matters because a script
phase only orders work inside its own target, so nothing would stop a parallel
target linking the framework while Gradle was still writing it.

`KmpLabSDK` carries two dependency edges on it, and they do different jobs:
`.target` orders the build, `.xcframework` does the linking. Neither is
sufficient alone.

The XCFramework is an intermediate, not a deliverable — `KmpLabSDK` is the only
thing consumers integrate, and it absorbs the Kotlin framework — so it lives in
Gradle's build directory at
`shared/network/build/XCFrameworks/<config>/KmpLabNetwork.xcframework`.

A pbxproj cannot vary an xcframework reference by configuration, so the manifest
links a fixed sibling path instead:
`shared/network/build/XCFrameworks/KmpLabNetwork.xcframework`. That is a symlink
which the `linkKmpLabNetwork{Debug,Release}XCFramework` tasks repoint at the
build type just requested. The foreign build picks the build type from
`$CONFIGURATION`, so Xcode and Kotlin never disagree.

When you build through Gradle, the framework is already built as a task
dependency, and `KMPLAB_XCFRAMEWORK_READY=1` tells the foreign build phase to
step aside — otherwise `xcodebuild` would start a second Gradle inside the first
and the two would contend for the same project lock. Building from Xcode
directly leaves the variable unset, and the phase runs Gradle itself.

One gotcha in that second case: Xcode's script environment is not your shell, so
`gradlew` may not find a JDK. If the phase fails with a Java error, set
`JAVA_HOME` explicitly in the script.

## Architectures

`:shared:network` builds `iosArm64` and `iosSimulatorArm64`. There is no
`iosX64`: that slice serves only the simulator on an Intel Mac, and it cost most
of the time in every Kotlin rebuild — 27.6s against 5.5s without it.

`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` in the shared settings is not
optional decoration. A framework carries an architecture-independent
Objective-C clang module and a **per-architecture** Swift overlay. If Xcode
builds x86_64 while Kotlin produces no x86_64 overlay, Swift silently falls back
to the clang module alone and every SKIE addition vanishes behind errors like
`cannot find 'onEnum' in scope` — with the framework itself perfectly intact.
The setting keeps both sides agreeing on which architectures exist.

It is scoped to the simulator SDK, so device builds keep `iosArm64`.

## Linking

Everything is static today. The Kotlin framework is built with `isStatic = true`
and `sdk` is a `.staticFramework`, so it all folds into the app binary at link
time.

That has a consequence for distribution: a static framework does not link its own
dependencies, so a consumer would need both the SDK and the Kotlin XCFramework,
and would see the Kotlin module. Making `KmpLabSDK` dynamic would absorb the
static Kotlin framework into one shippable artifact and hide it. That change has
not been made yet.

## SKIE

The framework is built with [SKIE](https://skie.touchlab.co), which rewrites the
generated Swift API: Kotlin sealed classes arrive as real Swift enums, switched
exhaustively through `onEnum(of:)` with the generic type argument intact; default
arguments become Swift overloads; and Flows become `AsyncSequence`. Without it
the Swift side casts its way through Objective-C, which erases all of that.
`ApiResultShowcase.swift` is what keeps that shape observable.

`produceDistributableFramework()` turns on Swift library evolution, so the
framework emits a `.swiftinterface` rather than a compiler-locked
`.swiftmodule`, along with `noClangModuleBreadcrumbsInStaticFrameworks`, which
exists for exactly this static setup. Strictly it may not be needed — nothing
outside `KmpLabSDK` consumes `KmpLabNetwork`, and both are built by the same
toolchain — but it becomes load-bearing the moment an SDK signature leaks a
Kotlin type.

Analytics are off. SKIE otherwise uploads to Touchlab on every framework link,
and a build should not need the network to succeed. `disableUpload.set(true)` is
the middle setting if you want to inspect what it would have sent.

`SWIFT_TREAT_WARNINGS_AS_ERRORS` is not set. It was left off for a SKIE
`swift-module-cache` warning on static XCFrameworks, but a clean build of either
scheme now produces no warnings at all, so that is an open question rather than a
constraint.

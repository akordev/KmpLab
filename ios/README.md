# iOS

The Swift half of the SDK. **None of the Swift here has been compiled yet** — it
was written on a Linux machine, which has no Swift iOS SDK. Treat it as a
reviewed design, not as working code, until it has been through Xcode once.

The Kotlin side is in better shape: `shared/network` does compile for
`iosArm64`, `iosSimulatorArm64`, and `iosX64` on Linux, producing real `.klib`
output. What Linux cannot do is *link* — `linkDebugFrameworkIos*` and
`assembleKmpLabNetwork*XCFramework` are skipped, because emitting a Mach-O
framework needs the Apple toolchain. So the Kotlin that goes into the
XCFramework is type-checked; the XCFramework itself has never been built.

## Building

Requires macOS with Xcode, plus [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`).

```bash
./ios/build-xcframework.sh release   # Gradle -> KmpLabNetwork.xcframework
cd ios/sample
xcodegen generate                    # project.yml -> KmpLabSample.xcodeproj
open KmpLabSample.xcodeproj
```

The XCFramework lands in `ios/sdk/Artifacts/` and is deliberately **not**
checked in — it is a build output, and a binary one at that. `Package.swift`
declares it as a `binaryTarget`, so the Swift package will not resolve until the
script has run at least once.

The Xcode project is generated rather than committed for the same reason a
`.pbxproj` is never worth reviewing: it is machine-written, merge-hostile, and
says nothing a `project.yml` does not say more clearly.

## SKIE

The framework is built with [SKIE](https://skie.touchlab.co), which rewrites the
generated Swift API so that:

- `NetworkResult` arrives as a **real Swift enum**. `switch onEnum(of: result)`
  is exhaustive, `success.body` is typed, and adding a Kotlin case breaks
  `NetworkBridge.swift` at compile time. Without SKIE this was a chain of `as?`
  casts against Objective-C lightweight generics, which are erased at runtime —
  the casts proved nothing and a new case fell through to a runtime fallback.
- Kotlin **default arguments** become Swift overloads, so `repos(login:perPage:)`
  can leave `page` at its Kotlin default.
- Kotlin **Flows** become `AsyncSequence`, if the SDK ever streams across the
  boundary.

`produceDistributableFramework()` is set because the XCFramework is consumed by
SwiftPM, potentially from a different Xcode version than the one that built it.
The framework is dynamic rather than static for the same reason.

## What to expect on the first compile

Everything that touches Kotlin lives in exactly one file:
`sdk/Sources/KmpLabSDK/Internal/NetworkBridge.swift`. If the interop is wrong,
that is the only file that needs to move — the public surface above it is plain
Swift.

**SKIE has never actually run.** It is a *linker* plugin: it does its work when a
framework is linked, and linking is skipped on Linux. What has been verified is
only that SKIE 0.10.14 applies cleanly and supports Kotlin 2.4.10 (its plugin jar
bundles a KGP shim for 2.4.0 and lists 2.4.10 among supported versions). Whether
the generated Swift matches what `NetworkBridge.swift` assumes is unknown.

Four things to check against the generated Swift API on the first real build:

1. **`Nothing` as a type argument.** `HttpFailure` and `TransportFailure` are
   declared `NetworkResult<Nothing>`, and Kotlin `Nothing` has no Swift
   equivalent. SKIE's sealed-class docs list only one limitation (sealed
   *interfaces* are not `Hashable`) and say nothing about `Nothing`. If it
   misbehaves, declare the failure branches generic (`HttpFailure<out T>`)
   instead of pinning them to `Nothing`.
2. **Enum case payload names.** The extensions assume the payload types are still
   the flattened Kotlin class names, `NetworkResultHttpFailure` and
   `NetworkResultTransportFailure`.
3. **`description_`.** Kotlin's `GitHubRepoDto.description` collides with
   Objective-C's `description`, so Kotlin/Native renames it with a trailing
   underscore. Confirm SKIE does not undo that.
4. **Primitive widths.** Kotlin `Int` arrives as `Int32` and `Long` as `Int64`;
   SKIE does not change primitive bridging.

If the bridge still turns out to be more friction than it is worth, the usual
escape hatch is a Swift-friendly facade in `shared/network/src/iosMain` that
flattens the result before it crosses the boundary. That was deliberately not
done, to keep the shared module purely about networking.

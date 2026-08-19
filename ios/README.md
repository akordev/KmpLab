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

## What to expect on the first compile

Everything that touches Kotlin lives in exactly one file:
`sdk/Sources/KmpLabSDK/Internal/NetworkBridge.swift`. If the interop is wrong,
that is the only file that needs to move — the public surface above it is plain
Swift.

Two specific things to check against the generated Objective-C header
(`KmpLabNetwork.xcframework/.../Headers/KmpLabNetwork.h`):

1. **Flattened nested names.** Kotlin's `NetworkResult.Success` is expected to
   arrive as `NetworkResultSuccess`. Confirm the exact spelling.
2. **Erased generics.** The casts use `NetworkResultSuccess<AnyObject>` because
   Objective-C lightweight generics are erased at runtime. If the concrete type
   argument is required instead, the casts need the DTO type spelled out.

Also worth knowing: Kotlin default arguments do not bridge, which is why every
`NetworkConfig` parameter is passed explicitly, and Kotlin `Int` arrives as
`Int32` while `Long` arrives as `Int64`.

If the bridge turns out to be more friction than it is worth, the usual escape
hatch is to add a Swift-friendly facade in `shared/network/src/iosMain` that
flattens the sealed generic result into plain types before it crosses the
boundary. That was deliberately not done here, to keep the shared module purely
about networking.

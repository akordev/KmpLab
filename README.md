# KmpLab

An experiment in shipping a **native SDK on two platforms from one network layer**.

The product here is the SDK, not the app. Android consumers get a Kotlin library,
iOS consumers get a Swift package, and both are backed by a single Kotlin
Multiplatform module that owns every HTTP call. The KMP module is an
implementation detail — no consumer of either SDK ever sees a `dev.akordev.kmplab.network`
type or has to know Kotlin Multiplatform is involved.

The SDK wraps a slice of the GitHub REST API: look up an account, list its
repositories, and report the rate-limit budget.

## Layout

```
shared/network/     Kotlin Multiplatform — the ONLY shared code.
                    Ktor client, DTOs, error handling, rate-limit parsing.
                    Targets: androidTarget, iosArm64, iosSimulatorArm64, iosX64.

android/sdk/        Android library. The public Kotlin SDK surface.
android/sample/     Compose app that integrates android/sdk as a third party would.

ios/sdk/            Swift package. The public Swift SDK surface, wrapping the
                    XCFramework that shared/network produces (built with SKIE,
                    so the Kotlin sealed types arrive as real Swift enums).
ios/sample/         SwiftUI app that integrates ios/sdk.
```

The dependency direction is one-way and enforced by Gradle: `android/sample`
depends on `android/sdk`, which depends on `shared/network` with
`implementation` (not `api`), so the KMP types never leak onto a consumer's
compile classpath.

## The shape of the SDK surface

Both platforms expose the same three things, in each language's idiom:

| | Android | iOS |
|---|---|---|
| Entry point | `KmpLabSdk.create()` | `KmpLabClient()` |
| Lookup | `suspend fun user(login:): SdkResult<GitHubUser>` | `func user(login:) async throws -> GitHubUser` |
| Failures | `SdkError` sealed interface | `SDKError` enum |
| Budget | `StateFlow<RateLimitStatus?>` | `var rateLimit: RateLimitStatus?` |

Failures are values, not exceptions — a 404 or a dead connection comes back as
data on both platforms. The one piece of real domain knowledge in the mapping is
that **GitHub signals an exhausted rate limit with a 403, not a 429**; both SDKs
fold that into the same `rateLimited` case so callers never have to know.

## Building

Toolchain versions are pinned in `mise.toml` (JDK 21, Gradle 9.7.0) because the
Android toolchain rejects newer JDKs. With [mise](https://mise.jdx.dev):

```bash
mise install
export ANDROID_HOME="$HOME/Android/Sdk"   # or set sdk.dir in local.properties
```

### Android

```bash
./gradlew :shared:network:testAndroidHostTest   # shared network layer tests
./gradlew :android:sdk:testDebugUnitTest        # SDK mapping tests
./gradlew :android:sample:assembleDebug         # APK
```

### iOS

Requires macOS with Xcode — Kotlin/Native cannot produce Apple binaries on
Linux, and neither can Swift build for iOS. See [ios/README.md](ios/README.md).

```bash
./ios/build-xcframework.sh release
cd ios/sample && xcodegen generate && open KmpLabSample.xcodeproj
```

## Status

**Verified on this Linux machine:**

- `shared/network` compiles for **all four targets** — `androidTarget`,
  `iosArm64`, `iosSimulatorArm64`, `iosX64` — main and test source sets alike.
  Kotlin/Native gets as far as a real `.klib` for the Apple targets on Linux.
- 17 unit tests pass: 7 against the shared network layer (Ktor `MockEngine`),
  10 against the Android SDK's DTO and error mapping.
- `android/sdk` builds, `android/sample` produces a debug APK.

**Cannot be verified without a Mac** — Gradle marks these tasks `SKIPPED` on
Linux rather than failing:

- Linking the iOS frameworks and assembling the XCFramework
  (`linkDebugFrameworkIos*`, `assembleKmpLabNetwork*XCFramework`).
- Running the iOS unit tests.
- Every line of Swift under `ios/`. It has never been through a compiler.
- SKIE itself. It is a linker plugin, so it never ran here — only that it applies
  cleanly and supports Kotlin 2.4.10 has been confirmed.

The obvious next step is a `macos-latest` GitHub Actions job to close that gap.

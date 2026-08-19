# KmpLab

Scaffold for a **native SDK on two platforms sharing one network layer**.

The product is the SDK, not an app. Android consumers get a Kotlin library, iOS
consumers get a Swift package, and both are meant to be backed by a single Kotlin
Multiplatform module that owns the HTTP calls. The KMP module stays an
implementation detail — `android/sdk` depends on it with `implementation` rather
than `api`, so no consumer sees a `dev.akordev.kmplab.network` type.

**This is structure and build configuration only.** There is no implementation
yet.

## Layout

```
shared/network/     Kotlin Multiplatform — the only shared code.
                    Targets: androidTarget, iosArm64, iosSimulatorArm64, iosX64.
                    Produces KmpLabNetwork.xcframework for the iOS side.

android/sdk/        Android library. The public Kotlin SDK surface.
android/sample/     Android app that integrates android/sdk.

ios/sdk/            Swift package over the XCFramework. The public Swift surface.
ios/sample/         iOS app that integrates ios/sdk.
```

## Toolchain

Pinned in `mise.toml`: **JDK 21, Gradle 9.7.0**. The Android toolchain rejects
newer JDKs, and this machine defaults to 26.

```bash
mise install
export ANDROID_HOME="$HOME/Android/Sdk"   # or set sdk.dir in local.properties
```

Notable versions: Kotlin 2.4.10, AGP 9.3.1, Ktor 3.5.2, SKIE 0.10.14. AGP 9 ships
Kotlin support built in, so there is no `kotlin-android` plugin anywhere;
`shared/network` uses `com.android.kotlin.multiplatform.library`.

## Building

```bash
./gradlew :android:sample:assembleDebug     # Android
./ios/build-xcframework.sh release          # iOS — macOS only
```

Kotlin/Native compiles the Apple targets to `.klib` on Linux, but **linking a
framework needs macOS** — Gradle skips those tasks here rather than failing. See
[ios/README.md](ios/README.md).

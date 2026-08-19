import ProjectDescription

// The public Swift SDK surface. Statically links KmpLabNetwork.xcframework, which
// :shared:network produces, so consumers never see a Kotlin type.
//
// The XCFramework is not committed. Gradle writes it here directly:
//
//   ./gradlew :shared:network:assembleKmpLabNetworkDebugXCFramework
//
// Building the sample app does it for you. For something to hand to consumers,
// swap Debug for Release — that lands in Artifacts/release.
let project = Project(
    name: "KmpLabSDK",
    targets: [
        .target(
            name: "KmpLabSDK",
            destinations: .iOS,
            product: .staticFramework,
            bundleId: "dev.akordev.kmplab.sdk",
            deploymentTargets: .iOS("15.0"),
            sources: ["Sources/**"],
            dependencies: [
                .xcframework(path: "Artifacts/debug/KmpLabNetwork.xcframework"),
            ]
        ),
    ]
)

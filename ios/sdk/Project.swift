import ProjectDescription

// The public Swift SDK surface. Statically links KmpLabNetwork.xcframework, which
// :shared:network produces, so consumers never see a Kotlin type.
//
// The XCFramework is not committed — run ../build-xcframework.sh, or build the
// sample app, whose pre-action does it for you.
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
                .xcframework(path: "Artifacts/KmpLabNetwork.xcframework"),
            ]
        ),
    ]
)

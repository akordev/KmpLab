import ProjectDescription
import ProjectDescriptionHelpers

// The sample app integrates KmpLabSDK exactly as a third party would.
//
// Nothing here mentions Gradle: the SDK project's KmpLabNetwork foreign build
// target carries that, and the dependency graph pulls it in.
let project = Project(
    name: "KmpLabSample",
    settings: .settings(base: KmpLab.baseSettings),
    targets: [
        .target(
            name: "KmpLabSample",
            destinations: .iOS,
            product: .app,
            bundleId: "dev.akordev.kmplab.sample",
            deploymentTargets: KmpLab.deploymentTargets,
            infoPlist: .extendingDefault(with: [
                "CFBundleDisplayName": "KmpLab Sample",
                "UILaunchScreen": [:],
            ]),
            buildableFolders: ["Sources"],
            dependencies: [
                .project(target: "KmpLabSDK", path: "../sdk"),
            ]
        ),
    ]
)

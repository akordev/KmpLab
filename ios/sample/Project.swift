import ProjectDescription

// The sample app integrates KmpLabSDK exactly as a third party would.
let project = Project(
    name: "KmpLabSample",
    targets: [
        .target(
            name: "KmpLabSample",
            destinations: .iOS,
            product: .app,
            bundleId: "dev.akordev.kmplab.sample",
            deploymentTargets: .iOS("15.0"),
            infoPlist: .extendingDefault(with: [
                "CFBundleDisplayName": "KmpLab Sample",
                "UILaunchScreen": [:],
            ]),
            sources: ["Sources/**"],
            scripts: [
                // Cmd+R rebuilds the Kotlin as well, so the app can never run
                // against a stale framework. Gradle is incremental, so this is
                // close to free once warm.
                .pre(
                    script: #"""
                    set -euo pipefail
                    "$SRCROOT/../build-xcframework.sh" debug
                    """#,
                    name: "Rebuild KmpLabNetwork.xcframework",
                    basedOnDependencyAnalysis: false
                ),
            ],
            dependencies: [
                .project(target: "KmpLabSDK", path: "../sdk"),
            ]
        ),
    ]
)

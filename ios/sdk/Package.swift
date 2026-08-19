// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "KmpLabSDK",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "KmpLabSDK", targets: ["KmpLabSDK"])
    ],
    targets: [
        // Produced by `../build-xcframework.sh`, which runs the Gradle task
        // :shared:network:assembleKmpLabNetworkReleaseXCFramework and copies the
        // result here. Not checked in — it is a build output.
        .binaryTarget(
            name: "KmpLabNetwork",
            path: "Artifacts/KmpLabNetwork.xcframework"
        ),
        .target(
            name: "KmpLabSDK",
            dependencies: ["KmpLabNetwork"]
        ),
        .testTarget(
            name: "KmpLabSDKTests",
            dependencies: ["KmpLabSDK"]
        ),
    ]
)

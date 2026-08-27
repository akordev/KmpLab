import ProjectDescription
import ProjectDescriptionHelpers

// The public Swift SDK surface. Links KmpLabNetwork.xcframework, which
// :shared:network produces, so consumers never see a Kotlin type.
//
// That XCFramework is never shipped on its own — KmpLabSDK is the only thing
// integrators consume — so it is an intermediate and lives in Gradle's build
// directory. The path below is a symlink Gradle repoints at the build type it
// last produced, because an xcframework reference in a pbxproj cannot vary by
// configuration.
//
// It is produced by the KmpLabNetwork target below — a foreign build, meaning
// Tuist models the Gradle invocation as a real node in the graph rather than as
// a script phase bolted to whichever target happened to need it.
let project = Project(
    name: "KmpLabSDK",
    settings: .settings(base: KmpLab.baseSettings),
    targets: [
        // Gradle builds this; Tuist owns the ordering. Anything depending on the
        // target gets the XCFramework built first, which a script phase on a
        // sibling target could not guarantee.
        .foreignBuild(
            name: "KmpLabNetwork",
            destinations: .iOS,
            script: #"""
            set -euo pipefail
            ROOT="${SRCROOT:-$PWD}/../.."
            # Set by the Gradle buildIos* tasks, which have already built the
            # framework as a dependency. Without this the phase would start a
            # second Gradle from inside the first, and the two would contend for
            # the same project lock.
            if [ "${KMPLAB_XCFRAMEWORK_READY:-0}" = "1" ]; then
                echo "note: XCFramework built by the invoking Gradle build"
                exit 0
            fi
            # Kotlin/Native release is a full LLVM optimization pass — minutes,
            # not seconds — so the build type has to follow Xcode's, not be
            # pinned. This is the only place that choice is made.
            case "${CONFIGURATION:-Debug}" in
                Release) KMP_BUILD_TYPE=Release ;;
                *)       KMP_BUILD_TYPE=Debug ;;
            esac
            "$ROOT/gradlew" -p "$ROOT" \
                ":shared:network:linkKmpLabNetwork${KMP_BUILD_TYPE}XCFramework"
            """#,
            // Deliberately no inputs, which means the script runs on every
            // build. Tuist expands a .folder input to the files present at
            // generation time, so a newly added Kotlin file would not be
            // tracked and Xcode would hand you a stale framework with no
            // warning. Gradle is the incremental build system here and a warm
            // no-op costs ~1.6s, which is the cheaper side of that trade.
            output: .xcframework(
                path: "../../shared/network/build/XCFrameworks/KmpLabNetwork.xcframework",
                linking: .static
            )
        ),
        .target(
            name: "KmpLabSDK",
            destinations: .iOS,
            // Dynamic, not static, and that is a distribution decision rather than
            // a linking preference. A static framework does not link its own
            // dependencies: consumers would have to add KmpLabNetwork.xcframework
            // themselves and would see every Kotlin type. Dynamic absorbs the
            // static Kotlin framework, so what ships is one file that reveals
            // nothing.
            product: .framework,
            bundleId: "dev.akordev.kmplab.sdk",
            deploymentTargets: KmpLab.deploymentTargets,
            // A buildable folder, not a glob: Xcode tracks the directory itself,
            // so adding or renaming a source file needs no `tuist generate`.
            buildableFolders: ["Sources"],
            dependencies: [
                // Both edges are required, and they do different jobs: .target
                // orders the build, .xcframework does the linking. Neither is
                // sufficient alone.
                .target(name: "KmpLabNetwork"),
                .xcframework(
                    path: "../../shared/network/build/XCFrameworks/KmpLabNetwork.xcframework"
                ),
            ],
            settings: .settings(base: [
                // Emit a .swiftinterface rather than a compiler-locked
                // .swiftmodule. Consumers build with a different Xcode than this,
                // and module stability is the whole reason that works.
                "BUILD_LIBRARY_FOR_DISTRIBUTION": "YES",

                // Kotlin/Native's Objective-C classes and categories come from a
                // static archive, and the linker drops anything no Swift symbol
                // references. -ObjC keeps them.
                "OTHER_LDFLAGS": "-ObjC",
            ])
        ),
    ]
)

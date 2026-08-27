import ProjectDescription

/// Build knobs shared by every iOS target, so a bump happens in one place
/// rather than once per manifest.
public enum KmpLab {

    /// Matches the iOS slices :shared:network builds.
    public static let deploymentTargets: DeploymentTargets = .iOS("15.0")

    public static let baseSettings: SettingsDictionary = [
        // Tuist defaults to 5, which leaves the Swift 6 compiler reporting
        // data-race violations as warnings. The SDK ships as a binary, so
        // whether its types are Sendable and what is actor-isolated are part of
        // its public contract — settle that now, while the surface is empty.
        // Adding Sendable later is additive; adding isolation later breaks
        // every consumer.
        "SWIFT_VERSION": "6.0",
    ]

    // Deliberately not here: SWIFT_TREAT_WARNINGS_AS_ERRORS. SKIE emits a
    // swift-module-cache warning for static XCFrameworks that would fail every
    // build. Revisit once that is gone.
}

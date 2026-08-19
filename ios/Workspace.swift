import ProjectDescription

// Tuist generates the Xcode projects from these manifests; nothing under ios/ is
// committed as a .xcodeproj. Run `tuist generate` from this directory.
let workspace = Workspace(
    name: "KmpLab",
    projects: [
        "sdk",
        "sample",
    ]
)

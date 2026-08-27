import ProjectDescription

// This file is what roots Tuist at ios/. Without it Tuist walks up to the
// closest ancestor holding a .git or Tuist directory — the repository root —
// and ProjectDescriptionHelpers would have to live there, away from the
// manifests that import it.
let tuist = Tuist()

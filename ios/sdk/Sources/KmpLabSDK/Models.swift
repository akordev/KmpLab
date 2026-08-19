import Foundation

/// A GitHub account, as the SDK presents it.
public struct GitHubUser: Equatable, Sendable {
    public let login: String
    public let displayName: String
    public let avatarURL: URL?
    public let profileURL: URL?
    public let bio: String?
    public let location: String?
    public let company: String?
    public let publicRepoCount: Int
    public let followers: Int
    public let following: Int
}

/// A repository owned by a ``GitHubUser``.
public struct Repository: Equatable, Sendable, Identifiable {
    public var id: String { fullName }

    public let name: String
    public let fullName: String
    public let url: URL?
    public let description: String?
    public let language: String?
    public let stars: Int
    public let forks: Int
    public let openIssues: Int
    public let isFork: Bool
    public let isArchived: Bool
    public let lastPushedAt: String?
}

/// How much of the GitHub rate-limit budget is left, as of the last call.
///
/// Anonymous callers get 60 requests an hour; an authenticated one gets 5000.
public struct RateLimitStatus: Equatable, Sendable {
    public let limit: Int
    public let remaining: Int
    public let resetAt: Date

    public var isExhausted: Bool { remaining <= 0 }
}

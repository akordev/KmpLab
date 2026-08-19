import Foundation

/// Configuration for ``KmpLabClient``.
///
/// Defaults talk to the public GitHub API anonymously, which is capped at 60
/// requests an hour — set `token` to lift it.
public struct KmpLabOptions: Sendable {
    public var token: String?
    public var baseURL: String
    public var userAgent: String
    public var requestTimeout: TimeInterval
    public var connectTimeout: TimeInterval
    public var enableLogging: Bool

    public init(
        token: String? = nil,
        baseURL: String = "https://api.github.com",
        userAgent: String = "KmpLab-iOS",
        requestTimeout: TimeInterval = 30,
        connectTimeout: TimeInterval = 15,
        enableLogging: Bool = false
    ) {
        self.token = token
        self.baseURL = baseURL
        self.userAgent = userAgent
        self.requestTimeout = requestTimeout
        self.connectTimeout = connectTimeout
        self.enableLogging = enableLogging
    }
}

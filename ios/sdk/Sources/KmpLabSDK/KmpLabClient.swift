import Foundation

/// Entry point to the KmpLab GitHub SDK.
///
/// ```swift
/// let client = KmpLabClient()
/// do {
///     let user = try await client.user(login: "akordev")
///     print(user.displayName)
/// } catch let error as SDKError {
///     print(error.localizedDescription)
/// }
/// ```
///
/// Instances own an `NSURLSession` connection pool, so create one per app and
/// call ``close()`` when you are done.
public actor KmpLabClient {

    private let bridge: NetworkBridge

    /// The rate-limit budget reported by the most recent call, or `nil` before
    /// the first one.
    public private(set) var rateLimit: RateLimitStatus?

    public init(options: KmpLabOptions = KmpLabOptions()) {
        self.bridge = NetworkBridge(options: options)
    }

    /// Looks up a single account by its login.
    public func user(login: String) async throws -> GitHubUser {
        let (result, budget) = await bridge.user(
            login: login.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        record(budget)
        return try result.get()
    }

    /// Lists an account's public repositories, most recently pushed first.
    public func repositories(login: String, limit: Int = 30) async throws -> [Repository] {
        let (result, budget) = await bridge.repositories(
            login: login.trimmingCharacters(in: .whitespacesAndNewlines),
            limit: limit
        )
        record(budget)
        return try result.get()
    }

    /// Releases the connection pool. The client is unusable afterwards.
    public func close() {
        bridge.close()
    }

    private func record(_ budget: RateLimitStatus?) {
        guard let budget else { return }
        rateLimit = budget
    }
}

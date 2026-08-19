import Foundation
import KmpLabNetwork

/// What a bridged call yields: the outcome, plus whatever rate-limit budget the
/// response carried.
typealias BridgeOutcome<T> = (result: Result<T, SDKError>, rateLimit: RateLimitStatus?)

// The ONLY file in this package that imports the Kotlin/Native framework.
// Everything Kotlin — the sealed hierarchy, `Int32` for Kotlin `Int` — is
// absorbed here so the public surface above stays plain Swift. If the Kotlin
// side changes shape, this is the one file that moves.
//
// SKIE does the heavy lifting: `NetworkResult` arrives as a real Swift enum via
// `onEnum(of:)`, so the switches below are exhaustive and `success.body` is
// typed. Adding a case in Kotlin breaks this file at compile time, which is the
// entire point.
struct NetworkBridge {

    private let client: GitHubNetworkClient

    init(options: KmpLabOptions) {
        self.client = GitHubNetworkClient(
            config: NetworkConfig(
                baseUrl: options.baseURL,
                token: options.token,
                userAgent: options.userAgent,
                requestTimeoutMillis: Int64(options.requestTimeout * 1000),
                connectTimeoutMillis: Int64(options.connectTimeout * 1000),
                logging: options.enableLogging
            )
        )
    }

    func close() {
        client.close()
    }

    func user(login: String) async -> BridgeOutcome<GitHubUser> {
        do {
            switch onEnum(of: try await client.user(login: login)) {
            case .success(let success):
                return (
                    .success(GitHubUser(dto: success.body)),
                    success.rateLimit.map(RateLimitStatus.init(kotlin:))
                )
            case .httpFailure(let failure):
                return failure.outcome(resource: login)
            case .transportFailure(let failure):
                return failure.outcome()
            }
        } catch {
            return unexpected(error)
        }
    }

    func repositories(login: String, limit: Int) async -> BridgeOutcome<[Repository]> {
        do {
            // `page` keeps its Kotlin default — SKIE generates the overload.
            switch onEnum(of: try await client.repos(login: login, perPage: Int32(limit))) {
            case .success(let success):
                return (
                    .success(success.body.map(Repository.init(dto:))),
                    success.rateLimit.map(RateLimitStatus.init(kotlin:))
                )
            case .httpFailure(let failure):
                return failure.outcome(resource: login)
            case .transportFailure(let failure):
                return failure.outcome()
            }
        } catch {
            return unexpected(error)
        }
    }

    /// A Kotlin exception escaping the network layer is a bug — it folds every
    /// expected failure into a value — but it must not take the host app down.
    private func unexpected<T>(_ error: Error) -> BridgeOutcome<T> {
        (.failure(.offline(message: error.localizedDescription)), nil)
    }
}

// MARK: - Kotlin failures -> Swift failures

private extension NetworkResultHttpFailure {
    /// GitHub signals an exhausted rate limit with a 403, not a 429. This is the
    /// same fold the Android SDK performs, kept deliberately identical.
    func outcome<T>(resource: String) -> BridgeOutcome<T> {
        let budget = rateLimit.map(RateLimitStatus.init(kotlin:))

        let error: SDKError
        switch status {
        case 404: error = .notFound(resource: resource)
        case 401: error = .unauthorized
        case 429: error = .rateLimited(resetAt: budget?.resetAt)
        case 403 where budget?.isExhausted == true: error = .rateLimited(resetAt: budget?.resetAt)
        default: error = .http(status: Int(status), message: message)
        }

        return (.failure(error), budget)
    }
}

private extension NetworkResultTransportFailure {
    func outcome<T>() -> BridgeOutcome<T> {
        (.failure(.offline(message: cause.message ?? "unknown")), nil)
    }
}

// MARK: - Kotlin DTOs -> Swift models

private extension RateLimitStatus {
    init(kotlin: RateLimit) {
        self.init(
            limit: Int(kotlin.limit),
            remaining: Int(kotlin.remaining),
            resetAt: Date(timeIntervalSince1970: TimeInterval(kotlin.resetAtEpochSeconds))
        )
    }
}

private extension GitHubUser {
    init(dto: GitHubUserDto) {
        self.init(
            login: dto.login,
            displayName: dto.name.nonBlank ?? dto.login,
            avatarURL: URL(string: dto.avatarUrl),
            profileURL: URL(string: dto.htmlUrl),
            bio: dto.bio.nonBlank,
            location: dto.location.nonBlank,
            company: dto.company.nonBlank,
            publicRepoCount: Int(dto.publicRepos),
            followers: Int(dto.followers),
            following: Int(dto.following)
        )
    }
}

private extension Repository {
    init(dto: GitHubRepoDto) {
        self.init(
            name: dto.name,
            fullName: dto.fullName,
            url: URL(string: dto.htmlUrl),
            description: dto.description_.nonBlank,
            language: dto.language,
            stars: Int(dto.stargazersCount),
            forks: Int(dto.forksCount),
            openIssues: Int(dto.openIssuesCount),
            isFork: dto.fork,
            isArchived: dto.archived,
            lastPushedAt: dto.pushedAt
        )
    }
}

private extension Optional where Wrapped == String {
    /// Blank optional fields collapse to nil rather than empty strings, matching
    /// the Android mapper.
    var nonBlank: String? {
        guard let value = self?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else { return nil }
        return value
    }
}

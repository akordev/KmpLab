import Foundation
import KmpLabNetwork

// The ONLY file in this package that imports the Kotlin/Native framework.
//
// Everything Kotlin — generic sealed classes, `Int32` for Kotlin `Int`,
// `KotlinThrowable` — is absorbed here, so the public surface above stays plain
// Swift. If the Kotlin side changes shape, this is the one file that moves.
//
// Note on naming: Kotlin/Native exports nested classes flattened, so
// `NetworkResult.Success` arrives as `NetworkResultSuccess`. ObjC lightweight
// generics are erased at runtime, which is why the casts below use `AnyObject`
// as the type argument rather than the concrete DTO.
struct NetworkBridge {

    private let client: GitHubNetworkClient

    init(options: KmpLabOptions) {
        // Kotlin default arguments do not bridge, so every parameter is explicit.
        let config = NetworkConfig(
            baseUrl: options.baseURL,
            token: options.token,
            userAgent: options.userAgent,
            requestTimeoutMillis: Int64(options.requestTimeout * 1000),
            connectTimeoutMillis: Int64(options.connectTimeout * 1000),
            logging: options.enableLogging
        )
        self.client = GitHubNetworkClient(config: config)
    }

    func close() {
        client.close()
    }

    func user(login: String) async -> (Result<GitHubUser, SDKError>, RateLimitStatus?) {
        await fold(
            resource: login,
            call: { try await client.user(login: login) },
            transform: { body in (body as? GitHubUserDto).map(GitHubUser.init(dto:)) }
        )
    }

    func repositories(login: String, limit: Int) async -> (Result<[Repository], SDKError>, RateLimitStatus?) {
        await fold(
            resource: login,
            call: { try await client.repos(login: login, perPage: Int32(limit), page: 1) },
            transform: { body in
                (body as? [GitHubRepoDto]).map { $0.map(Repository.init(dto:)) }
            }
        )
    }

    // MARK: - Folding Kotlin results into Swift ones

    private func fold<T>(
        resource: String,
        call: () async throws -> Any,
        transform: (Any?) -> T?
    ) async -> (Result<T, SDKError>, RateLimitStatus?) {
        do {
            let result = try await call()

            if let success = result as? NetworkResultSuccess<AnyObject> {
                let budget = success.rateLimit.map(RateLimitStatus.init(kotlin:))
                guard let value = transform(success.body) else {
                    return (.failure(.http(status: 200, message: "Unexpected payload shape")), budget)
                }
                return (.success(value), budget)
            }

            if let failure = result as? NetworkResultHttpFailure {
                let budget = failure.rateLimit.map(RateLimitStatus.init(kotlin:))
                return (.failure(failure.toSDKError(resource: resource, budget: budget)), budget)
            }

            if let failure = result as? NetworkResultTransportFailure {
                return (.failure(.offline(message: failure.cause.message ?? "unknown")), nil)
            }

            return (.failure(.http(status: -1, message: "Unrecognised network result")), nil)
        } catch {
            // A Kotlin exception escaping the network layer is a bug, not an
            // expected failure — but it must not crash the host app.
            return (.failure(.offline(message: error.localizedDescription)), nil)
        }
    }
}

// MARK: - Kotlin -> Swift mapping

private extension NetworkResultHttpFailure {
    /// GitHub reports an exhausted budget as 403, not 429 — the same fold the
    /// Android SDK performs, kept deliberately identical.
    func toSDKError(resource: String, budget: RateLimitStatus?) -> SDKError {
        switch status {
        case 404:
            return .notFound(resource: resource)
        case 401:
            return .unauthorized
        case 429:
            return .rateLimited(resetAt: budget?.resetAt)
        case 403 where budget?.isExhausted == true:
            return .rateLimited(resetAt: budget?.resetAt)
        default:
            return .http(status: Int(status), message: message)
        }
    }
}

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
        let name = dto.name?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.init(
            login: dto.login,
            displayName: (name?.isEmpty == false ? name! : dto.login),
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

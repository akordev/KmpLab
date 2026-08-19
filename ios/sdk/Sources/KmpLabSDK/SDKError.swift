import Foundation

/// Why a call did not produce data.
///
/// Mirrors `SdkError` on the Android side: the cases a caller is likely to
/// branch on get their own case, and anything else lands in ``http(status:message:)``.
public enum SDKError: Error, Equatable, Sendable {

    /// No such user or repository.
    case notFound(resource: String)

    /// The GitHub rate limit is spent. Supply a token, or wait until `resetAt`.
    case rateLimited(resetAt: Date?)

    /// The token was missing, expired, or lacks the scope for this call.
    case unauthorized

    /// Any other non-2xx response.
    case http(status: Int, message: String)

    /// The request never reached GitHub: no connectivity, DNS, TLS, timeout.
    case offline(message: String)
}

extension SDKError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .notFound(let resource):
            return "No GitHub account called \"\(resource)\"."
        case .rateLimited:
            return "GitHub rate limit reached. Add a token, or wait for the reset."
        case .unauthorized:
            return "That token was rejected by GitHub."
        case .http(let status, let message):
            return "GitHub returned \(status): \(message)"
        case .offline:
            return "Could not reach GitHub. Check your connection."
        }
    }
}

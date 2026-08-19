package dev.akordev.kmplab.sdk

/**
 * Why a call did not produce data.
 *
 * The cases a caller is likely to branch on get their own type; anything else
 * lands in [Http] with the raw status.
 */
public sealed interface SdkError {

    /** No such user or repository. */
    public data class NotFound(val resource: String) : SdkError

    /** The GitHub rate limit is spent. Supply a token, or wait until the reset. */
    public data class RateLimited(val resetAtEpochSeconds: Long?) : SdkError

    /** The token was missing, expired, or lacks the scope for this call. */
    public data object Unauthorized : SdkError

    /** Any other non-2xx response. */
    public data class Http(val status: Int, val message: String) : SdkError

    /** The request never reached GitHub: no connectivity, DNS, TLS, timeout. */
    public data class Offline(val cause: Throwable) : SdkError
}

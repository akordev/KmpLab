package dev.akordev.kmplab.sdk.model

/**
 * How much of the GitHub rate-limit budget is left, as of the last call the SDK
 * made. Anonymous callers get 60 requests an hour; an authenticated one gets 5000.
 */
public data class RateLimitStatus(
    val limit: Int,
    val remaining: Int,
    val resetAtEpochSeconds: Long,
) {
    public val isExhausted: Boolean get() = remaining <= 0
}

package dev.akordev.kmplab.network

/**
 * The rate-limit budget GitHub reported on the most recent response, parsed
 * from the `x-ratelimit-*` headers. Absent when the response carried no such
 * headers (an error from an intermediary, for instance).
 */
data class RateLimit(
    val limit: Int,
    val remaining: Int,
    val resetAtEpochSeconds: Long,
) {
    val isExhausted: Boolean get() = remaining <= 0
}

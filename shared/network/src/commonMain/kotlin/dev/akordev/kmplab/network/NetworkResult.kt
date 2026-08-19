package dev.akordev.kmplab.network

/**
 * Outcome of a single call.
 *
 * The network layer never throws for an expected failure — a 404 or a dropped
 * connection is a value, not an exception. Only cancellation propagates.
 */
sealed class NetworkResult<out T> {

    /** A 2xx response with a decoded [body]. */
    data class Success<out T>(
        val body: T,
        val rateLimit: RateLimit?,
    ) : NetworkResult<T>()

    /** A non-2xx response. [message] is GitHub's own error text where it sent one. */
    data class HttpFailure(
        val status: Int,
        val message: String,
        val rateLimit: RateLimit?,
    ) : NetworkResult<Nothing>()

    /** The request never produced a response: DNS, TLS, timeout, offline. */
    data class TransportFailure(
        val cause: Throwable,
    ) : NetworkResult<Nothing>()
}

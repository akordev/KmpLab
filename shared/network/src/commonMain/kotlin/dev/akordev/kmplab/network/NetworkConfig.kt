package dev.akordev.kmplab.network

/**
 * Tuning knobs for [GitHubNetworkClient].
 *
 * Every value has a working default, so `NetworkConfig()` is enough to talk to
 * the public GitHub API anonymously. Supply a [token] to lift the anonymous
 * rate limit from 60 to 5000 requests per hour.
 */
data class NetworkConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val token: String? = null,
    val userAgent: String = DEFAULT_USER_AGENT,
    val requestTimeoutMillis: Long = 30_000,
    val connectTimeoutMillis: Long = 15_000,
    val logging: Boolean = false,
) {
    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.github.com"
        const val DEFAULT_USER_AGENT: String = "KmpLab-SDK"
    }
}

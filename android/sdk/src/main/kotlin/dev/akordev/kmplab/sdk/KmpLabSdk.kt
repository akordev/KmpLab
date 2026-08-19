package dev.akordev.kmplab.sdk

import dev.akordev.kmplab.network.GitHubNetworkClient
import dev.akordev.kmplab.network.NetworkConfig
import dev.akordev.kmplab.network.NetworkResult
import dev.akordev.kmplab.sdk.internal.toDomain
import dev.akordev.kmplab.sdk.internal.toSdkResult
import dev.akordev.kmplab.sdk.model.GitHubUser
import dev.akordev.kmplab.sdk.model.RateLimitStatus
import dev.akordev.kmplab.sdk.model.Repository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Entry point to the KmpLab GitHub SDK.
 *
 * ```kotlin
 * val sdk = KmpLabSdk.create()
 * when (val result = sdk.user("akordev")) {
 *     is SdkResult.Success -> println(result.data.displayName)
 *     is SdkResult.Failure -> println(result.error)
 * }
 * ```
 *
 * Instances own an HTTP connection pool, so create one per application and
 * [close] it when you are done. Calls are safe from any thread and always run
 * off the main dispatcher.
 */
public class KmpLabSdk internal constructor(
    private val network: GitHubNetworkClient,
    private val dispatcher: CoroutineDispatcher,
) {

    private val mutableRateLimit = MutableStateFlow<RateLimitStatus?>(null)

    /**
     * The rate-limit budget reported by the most recent call, or `null` before
     * the first one. Useful for showing the user why requests started failing.
     */
    public val rateLimit: StateFlow<RateLimitStatus?> = mutableRateLimit.asStateFlow()

    /** Looks up a single account by its login. */
    public suspend fun user(login: String): SdkResult<GitHubUser> = withContext(dispatcher) {
        network.user(login.trim())
            .also { it.recordRateLimit() }
            .toSdkResult(resource = login) { dto -> dto.toDomain() }
    }

    /** Lists an account's public repositories, most recently pushed first. */
    public suspend fun repositories(
        login: String,
        limit: Int = DEFAULT_LIMIT,
    ): SdkResult<List<Repository>> = withContext(dispatcher) {
        network.repos(login.trim(), perPage = limit)
            .also { it.recordRateLimit() }
            .toSdkResult(resource = login) { dtos -> dtos.map { it.toDomain() } }
    }

    /** Releases the connection pool. The instance is unusable afterwards. */
    public fun close() {
        network.close()
    }

    private fun NetworkResult<*>.recordRateLimit() {
        val budget = when (this) {
            is NetworkResult.Success -> rateLimit
            is NetworkResult.HttpFailure -> rateLimit
            is NetworkResult.TransportFailure -> null
        }
        budget?.let { mutableRateLimit.value = it.toDomain() }
    }

    public companion object {
        private const val DEFAULT_LIMIT = 30

        /** Builds an SDK instance. */
        public fun create(options: KmpLabSdkOptions = KmpLabSdkOptions()): KmpLabSdk =
            KmpLabSdk(
                network = GitHubNetworkClient(
                    NetworkConfig(
                        baseUrl = options.baseUrl,
                        token = options.token,
                        userAgent = options.userAgent,
                        requestTimeoutMillis = options.requestTimeoutMillis,
                        logging = options.enableLogging,
                    ),
                ),
                dispatcher = Dispatchers.IO,
            )
    }
}

package dev.akordev.kmplab.network

import dev.akordev.kmplab.network.internal.buildHttpClient
import dev.akordev.kmplab.network.model.GitHubErrorDto
import dev.akordev.kmplab.network.model.GitHubRepoDto
import dev.akordev.kmplab.network.model.GitHubUserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * The shared network layer: every HTTP call the SDK makes goes through here,
 * once, for both platforms.
 *
 * This type is internal to the SDK. Android consumers see `KmpLabSdk`, iOS
 * consumers see `KmpLabClient` — neither ever holds one of these directly.
 */
class GitHubNetworkClient internal constructor(
    private val http: HttpClient,
) {

    constructor(config: NetworkConfig = NetworkConfig()) : this(buildHttpClient(config))

    /** `GET /users/{login}` */
    suspend fun user(login: String): NetworkResult<GitHubUserDto> =
        fetch("users/$login") { it.body<GitHubUserDto>() }

    /** `GET /users/{login}/repos`, most recently pushed first. */
    suspend fun repos(
        login: String,
        perPage: Int = DEFAULT_PAGE_SIZE,
        page: Int = 1,
    ): NetworkResult<List<GitHubRepoDto>> =
        fetch(
            path = "users/$login/repos",
            configure = {
                parameter("per_page", perPage.coerceIn(1, MAX_PAGE_SIZE))
                parameter("page", page.coerceAtLeast(1))
                parameter("sort", "pushed")
                parameter("direction", "desc")
            },
        ) { it.body<List<GitHubRepoDto>>() }

    /** Releases the underlying engine. The client is unusable afterwards. */
    fun close() {
        http.close()
    }

    private suspend fun <T> fetch(
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
        decode: suspend (HttpResponse) -> T,
    ): NetworkResult<T> = try {
        val response = http.get(path) { configure() }
        val rateLimit = response.rateLimit()
        if (response.status.isSuccess()) {
            NetworkResult.Success(decode(response), rateLimit)
        } else {
            NetworkResult.HttpFailure(response.status.value, response.errorMessage(), rateLimit)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        NetworkResult.TransportFailure(failure)
    }

    private fun HttpResponse.rateLimit(): RateLimit? {
        val limit = headers[HEADER_LIMIT]?.toIntOrNull() ?: return null
        val remaining = headers[HEADER_REMAINING]?.toIntOrNull() ?: return null
        val reset = headers[HEADER_RESET]?.toLongOrNull() ?: return null
        return RateLimit(limit = limit, remaining = remaining, resetAtEpochSeconds = reset)
    }

    private suspend fun HttpResponse.errorMessage(): String =
        runCatching { body<GitHubErrorDto>().message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: status.description

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
        const val MAX_PAGE_SIZE = 100
        const val HEADER_LIMIT = "x-ratelimit-limit"
        const val HEADER_REMAINING = "x-ratelimit-remaining"
        const val HEADER_RESET = "x-ratelimit-reset"
    }
}

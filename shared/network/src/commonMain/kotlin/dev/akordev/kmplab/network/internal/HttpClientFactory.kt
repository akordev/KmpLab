package dev.akordev.kmplab.network.internal

import dev.akordev.kmplab.network.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the engine-backed client for the current platform: OkHttp on Android,
 * NSURLSession on iOS. Kept behind expect/actual so nothing above it has to care.
 */
internal expect fun platformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient

internal val KmpLabJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Assembles the configured client. Pass [engine] to bypass the platform engine —
 * that is how the tests substitute a [io.ktor.client.engine.mock.MockEngine].
 */
internal fun buildHttpClient(
    config: NetworkConfig,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        // A 404 or a 403 is a value we want to inspect, not an exception to catch.
        expectSuccess = false

        install(ContentNegotiation) { json(KmpLabJson) }

        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.connectTimeoutMillis
        }

        if (config.logging) {
            install(Logging) { level = LogLevel.INFO }
        }

        defaultRequest {
            url(config.baseUrl.withTrailingSlash())
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
            header(HttpHeaders.UserAgent, "${config.userAgent} ($platformName)")
            config.token?.let { bearerAuth(it) }
        }
    }
    return if (engine != null) HttpClient(engine, configure) else platformHttpClient(configure)
}

/**
 * Ktor resolves a relative request path against the default URL, and that only
 * keeps the base path if it ends in a slash. `api.github.com` -> `api.github.com/`.
 */
internal fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private const val GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version"
private const val GITHUB_API_VERSION = "2022-11-28"

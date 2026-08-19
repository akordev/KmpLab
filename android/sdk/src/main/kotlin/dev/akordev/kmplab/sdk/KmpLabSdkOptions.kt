package dev.akordev.kmplab.sdk

/**
 * Configuration for [KmpLabSdk.create]. Defaults talk to the public GitHub API
 * anonymously, which is capped at 60 requests an hour — set [token] to lift it.
 */
public data class KmpLabSdkOptions(
    val token: String? = null,
    val baseUrl: String = "https://api.github.com",
    val userAgent: String = "KmpLab-Android",
    val requestTimeoutMillis: Long = 30_000,
    val enableLogging: Boolean = false,
)

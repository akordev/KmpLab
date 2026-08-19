package dev.akordev.kmplab.network.model

import kotlinx.serialization.Serializable

/** GitHub's error envelope, e.g. `{"message":"Not Found","status":"404"}`. */
@Serializable
internal data class GitHubErrorDto(
    val message: String = "",
    @kotlinx.serialization.SerialName("documentation_url") val documentationUrl: String? = null,
)

package dev.akordev.kmplab.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape of `GET /users/{login}`. */
@Serializable
data class GitHubUserDto(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val bio: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)

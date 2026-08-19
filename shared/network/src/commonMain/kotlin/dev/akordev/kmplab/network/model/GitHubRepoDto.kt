package dev.akordev.kmplab.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape of one entry in `GET /users/{login}/repos`. */
@Serializable
data class GitHubRepoDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("pushed_at") val pushedAt: String? = null,
)

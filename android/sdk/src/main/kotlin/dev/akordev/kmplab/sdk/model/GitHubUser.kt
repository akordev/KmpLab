package dev.akordev.kmplab.sdk.model

/** A GitHub account, as the SDK presents it. */
public data class GitHubUser(
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val profileUrl: String,
    val bio: String?,
    val location: String?,
    val company: String?,
    val publicRepoCount: Int,
    val followers: Int,
    val following: Int,
)

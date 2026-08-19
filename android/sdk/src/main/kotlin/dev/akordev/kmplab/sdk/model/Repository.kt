package dev.akordev.kmplab.sdk.model

/** A repository owned by a [GitHubUser]. */
public data class Repository(
    val name: String,
    val fullName: String,
    val url: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val isFork: Boolean,
    val isArchived: Boolean,
    val lastPushedAt: String?,
)

package dev.akordev.kmplab.sdk.internal

import dev.akordev.kmplab.network.NetworkResult
import dev.akordev.kmplab.network.RateLimit
import dev.akordev.kmplab.network.model.GitHubRepoDto
import dev.akordev.kmplab.network.model.GitHubUserDto
import dev.akordev.kmplab.sdk.SdkError
import dev.akordev.kmplab.sdk.SdkResult
import dev.akordev.kmplab.sdk.model.GitHubUser
import dev.akordev.kmplab.sdk.model.RateLimitStatus
import dev.akordev.kmplab.sdk.model.Repository

/**
 * The boundary between the shared network layer and the public SDK surface.
 * Wire types stop here; nothing above this file mentions a DTO.
 */
internal fun GitHubUserDto.toDomain(): GitHubUser = GitHubUser(
    login = login,
    displayName = name?.takeIf { it.isNotBlank() } ?: login,
    avatarUrl = avatarUrl,
    profileUrl = htmlUrl,
    bio = bio?.takeIf { it.isNotBlank() },
    location = location?.takeIf { it.isNotBlank() },
    company = company?.takeIf { it.isNotBlank() },
    publicRepoCount = publicRepos,
    followers = followers,
    following = following,
)

internal fun GitHubRepoDto.toDomain(): Repository = Repository(
    name = name,
    fullName = fullName,
    url = htmlUrl,
    description = description?.takeIf { it.isNotBlank() },
    language = language,
    stars = stargazersCount,
    forks = forksCount,
    openIssues = openIssuesCount,
    isFork = fork,
    isArchived = archived,
    lastPushedAt = pushedAt,
)

internal fun RateLimit.toDomain(): RateLimitStatus = RateLimitStatus(
    limit = limit,
    remaining = remaining,
    resetAtEpochSeconds = resetAtEpochSeconds,
)

/**
 * Folds a transport-level result into the SDK's own vocabulary.
 *
 * The status-code mapping is where an SDK earns its keep: a caller should be
 * able to branch on "rate limited" without knowing that GitHub signals it with
 * a 403 and a spent budget rather than a 429.
 */
internal fun <D, T> NetworkResult<D>.toSdkResult(
    resource: String,
    transform: (D) -> T,
): SdkResult<T> = when (this) {
    is NetworkResult.Success -> SdkResult.Success(transform(body))

    is NetworkResult.TransportFailure -> SdkResult.Failure(SdkError.Offline(cause))

    is NetworkResult.HttpFailure -> SdkResult.Failure(
        when {
            status == 404 -> SdkError.NotFound(resource)
            status == 401 -> SdkError.Unauthorized
            status == 429 -> SdkError.RateLimited(rateLimit?.resetAtEpochSeconds)
            // GitHub reports an exhausted budget as 403, not 429.
            status == 403 && rateLimit?.isExhausted == true ->
                SdkError.RateLimited(rateLimit?.resetAtEpochSeconds)
            else -> SdkError.Http(status, message)
        },
    )
}

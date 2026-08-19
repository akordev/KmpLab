package dev.akordev.kmplab.sdk

import dev.akordev.kmplab.network.model.GitHubRepoDto
import dev.akordev.kmplab.network.model.GitHubUserDto
import dev.akordev.kmplab.sdk.internal.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainMappingTest {

    @Test
    fun `displayName falls back to the login when the account has no name`() {
        assertEquals("akordev", userDto(name = null).toDomain().displayName)
        assertEquals("akordev", userDto(name = "   ").toDomain().displayName)
        assertEquals("Ako Dev", userDto(name = "Ako Dev").toDomain().displayName)
    }

    @Test
    fun `blank optional fields collapse to null rather than empty strings`() {
        val user = userDto(bio = "", location = "  ", company = "").toDomain()

        assertNull(user.bio)
        assertNull(user.location)
        assertNull(user.company)
    }

    @Test
    fun `repo counts and flags survive the mapping`() {
        val repo = GitHubRepoDto(
            id = 1,
            name = "KmpLab",
            fullName = "akordev/KmpLab",
            htmlUrl = "https://github.com/akordev/KmpLab",
            description = "Experiments",
            language = "Kotlin",
            stargazersCount = 12,
            forksCount = 3,
            openIssuesCount = 4,
            fork = true,
            archived = true,
            pushedAt = "2026-08-19T09:00:00Z",
        ).toDomain()

        assertEquals("akordev/KmpLab", repo.fullName)
        assertEquals(12, repo.stars)
        assertEquals(3, repo.forks)
        assertEquals(4, repo.openIssues)
        assertEquals(true, repo.isFork)
        assertEquals(true, repo.isArchived)
        assertEquals("Kotlin", repo.language)
    }

    private fun userDto(
        name: String? = "Ako Dev",
        bio: String? = "Kotlin Multiplatform",
        location: String? = "Amsterdam",
        company: String? = "Independent",
    ) = GitHubUserDto(
        login = "akordev",
        id = 42,
        avatarUrl = "https://avatars.githubusercontent.com/u/42",
        htmlUrl = "https://github.com/akordev",
        name = name,
        bio = bio,
        location = location,
        company = company,
        publicRepos = 7,
        followers = 3,
        following = 5,
    )
}

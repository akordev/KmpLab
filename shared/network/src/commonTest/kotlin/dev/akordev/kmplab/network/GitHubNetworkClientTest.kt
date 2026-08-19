package dev.akordev.kmplab.network

import dev.akordev.kmplab.network.internal.buildHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubNetworkClientTest {

    @Test
    fun `user decodes the payload and the rate limit budget`() = runTest {
        val client = clientReturning(USER_JSON)

        val result = client.user("akordev")

        val success = assertIs<NetworkResult.Success<*>>(result)
        val user = assertIs<dev.akordev.kmplab.network.model.GitHubUserDto>(success.body)
        assertEquals("akordev", user.login)
        assertEquals(42, user.id)
        assertEquals("Ako Dev", user.name)
        assertEquals(7, user.publicRepos)

        val rateLimit = assertNotNull(success.rateLimit)
        assertEquals(60, rateLimit.limit)
        assertEquals(57, rateLimit.remaining)
        assertTrue(!rateLimit.isExhausted)
    }

    @Test
    fun `user resolves against the base url without dropping the host`() = runTest {
        lateinit var seen: HttpRequestData
        val client = clientRecording({ seen = it }, USER_JSON)

        client.user("akordev")

        assertEquals("api.github.com", seen.url.host)
        assertEquals("/users/akordev", seen.url.encodedPath)
    }

    @Test
    fun `user surfaces the GitHub message on a 404 rather than throwing`() = runTest {
        val client = clientReturning(
            body = """{"message":"Not Found","documentation_url":"https://docs.github.com"}""",
            status = HttpStatusCode.NotFound,
        )

        val failure = assertIs<NetworkResult.HttpFailure>(client.user("nope"))

        assertEquals(404, failure.status)
        assertEquals("Not Found", failure.message)
    }

    @Test
    fun `an exhausted rate limit still comes back as a value`() = runTest {
        val client = clientReturning(
            body = """{"message":"API rate limit exceeded"}""",
            status = HttpStatusCode.Forbidden,
            rateLimitRemaining = "0",
        )

        val failure = assertIs<NetworkResult.HttpFailure>(client.user("akordev"))

        assertEquals(403, failure.status)
        assertContains(failure.message, "rate limit")
        assertTrue(assertNotNull(failure.rateLimit).isExhausted)
    }

    @Test
    fun `repos asks for the newest page first and clamps the page size`() = runTest {
        lateinit var seen: HttpRequestData
        val client = clientRecording({ seen = it }, REPOS_JSON)

        val result = client.repos("akordev", perPage = 500)

        val success = assertIs<NetworkResult.Success<*>>(result)
        val repos = assertIs<List<*>>(success.body)
        assertEquals(1, repos.size)

        assertEquals("100", seen.url.parameters["per_page"])
        assertEquals("1", seen.url.parameters["page"])
        assertEquals("pushed", seen.url.parameters["sort"])
        assertEquals("desc", seen.url.parameters["direction"])
    }

    @Test
    fun `a token becomes a bearer credential and no token sends none`() = runTest {
        lateinit var authed: HttpRequestData
        GitHubNetworkClient(
            buildHttpClient(NetworkConfig(token = "ghp_secret"), recordingEngine({ authed = it }, USER_JSON)),
        ).user("akordev")
        assertEquals("Bearer ghp_secret", authed.headers[HttpHeaders.Authorization])

        lateinit var anonymous: HttpRequestData
        GitHubNetworkClient(
            buildHttpClient(NetworkConfig(), recordingEngine({ anonymous = it }, USER_JSON)),
        ).user("akordev")
        assertEquals(null, anonymous.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a dead connection is reported rather than thrown`() = runTest {
        val client = GitHubNetworkClient(
            buildHttpClient(NetworkConfig(), MockEngine { error("connection reset") }),
        )

        val failure = assertIs<NetworkResult.TransportFailure>(client.user("akordev"))

        assertContains(failure.cause.message.orEmpty(), "connection reset")
    }

    // --- helpers ----------------------------------------------------------

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        rateLimitRemaining: String = "57",
    ) = GitHubNetworkClient(
        buildHttpClient(NetworkConfig(), MockEngine(handler(body, status, rateLimitRemaining))),
    )

    private fun clientRecording(record: (HttpRequestData) -> Unit, body: String) =
        GitHubNetworkClient(buildHttpClient(NetworkConfig(), recordingEngine(record, body)))

    private fun recordingEngine(record: (HttpRequestData) -> Unit, body: String) =
        MockEngine { request ->
            record(request)
            handler(body, HttpStatusCode.OK, "57").invoke(this, request)
        }

    private fun handler(
        body: String,
        status: HttpStatusCode,
        rateLimitRemaining: String,
    ): MockRequestHandler = {
        respond(
            content = body,
            status = status,
            headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "x-ratelimit-limit" to listOf("60"),
                "x-ratelimit-remaining" to listOf(rateLimitRemaining),
                "x-ratelimit-reset" to listOf("1755600000"),
            ),
        )
    }

    private companion object {
        const val USER_JSON = """
            {
              "login": "akordev",
              "id": 42,
              "avatar_url": "https://avatars.githubusercontent.com/u/42",
              "html_url": "https://github.com/akordev",
              "name": "Ako Dev",
              "bio": "Kotlin Multiplatform experiments",
              "public_repos": 7,
              "followers": 3,
              "following": 5,
              "unexpected_field_the_api_added_later": true
            }
        """

        const val REPOS_JSON = """
            [
              {
                "id": 1,
                "name": "KmpLab",
                "full_name": "akordev/KmpLab",
                "html_url": "https://github.com/akordev/KmpLab",
                "description": "Experiments",
                "language": "Kotlin",
                "stargazers_count": 12,
                "forks_count": 1,
                "open_issues_count": 0,
                "fork": false,
                "archived": false,
                "pushed_at": "2026-08-19T09:00:00Z"
              }
            ]
        """
    }
}

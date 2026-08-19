package dev.akordev.kmplab.sdk

import dev.akordev.kmplab.network.NetworkResult
import dev.akordev.kmplab.network.RateLimit
import dev.akordev.kmplab.sdk.internal.toSdkResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The status-code fold is the part of the SDK a consumer actually depends on:
 * they branch on [SdkError], never on a raw status.
 */
class ErrorMappingTest {

    @Test
    fun `404 becomes NotFound carrying the resource that was asked for`() {
        val result = httpFailure(status = 404, message = "Not Found")
            .toSdkResult(resource = "ghost") { it }

        val error = assertIs<SdkResult.Failure>(result).error
        assertEquals(SdkError.NotFound("ghost"), error)
    }

    @Test
    fun `401 becomes Unauthorized`() {
        val result = httpFailure(status = 401, message = "Bad credentials")
            .toSdkResult(resource = "akordev") { it }

        assertEquals(SdkError.Unauthorized, assertIs<SdkResult.Failure>(result).error)
    }

    @Test
    fun `403 with a spent budget is a rate limit, not a generic http error`() {
        val result = httpFailure(
            status = 403,
            message = "API rate limit exceeded",
            rateLimit = RateLimit(limit = 60, remaining = 0, resetAtEpochSeconds = 1_755_600_000),
        ).toSdkResult(resource = "akordev") { it }

        val error = assertIs<SdkResult.Failure>(result).error
        assertEquals(SdkError.RateLimited(1_755_600_000), error)
    }

    @Test
    fun `403 with budget to spare stays a plain http error`() {
        val result = httpFailure(
            status = 403,
            message = "Repository access blocked",
            rateLimit = RateLimit(limit = 60, remaining = 41, resetAtEpochSeconds = 1_755_600_000),
        ).toSdkResult(resource = "akordev") { it }

        val error = assertIs<SdkResult.Failure>(result).error
        assertEquals(SdkError.Http(403, "Repository access blocked"), error)
    }

    @Test
    fun `429 is a rate limit even without headers to read`() {
        val result = httpFailure(status = 429, message = "Too Many Requests")
            .toSdkResult(resource = "akordev") { it }

        assertEquals(SdkError.RateLimited(null), assertIs<SdkResult.Failure>(result).error)
    }

    @Test
    fun `a transport failure becomes Offline and keeps the cause`() {
        val cause = IOException("no route to host")

        val result = NetworkResult.TransportFailure(cause).toSdkResult(resource = "akordev") { it }

        assertEquals(SdkError.Offline(cause), assertIs<SdkResult.Failure>(result).error)
    }

    @Test
    fun `success runs the transform`() {
        val result = NetworkResult.Success(body = 21, rateLimit = null)
            .toSdkResult(resource = "akordev") { it * 2 }

        assertEquals(42, assertIs<SdkResult.Success<Int>>(result).data)
    }

    private fun httpFailure(
        status: Int,
        message: String,
        rateLimit: RateLimit? = null,
    ) = NetworkResult.HttpFailure(status = status, message = message, rateLimit = rateLimit)
}

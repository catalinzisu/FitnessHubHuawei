package ro.fitnesshub.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.fitnesshub.api.ProfileDto
import ro.fitnesshub.api.WorkoutRequest
import ro.fitnesshub.data.auth.TokenProvider
import java.io.IOException

/** The API client against a fake server (Ktor MockEngine): tokens, statuses and network errors. */
class FitnessApiTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Records every request the client makes. */
    private val requests = mutableListOf<HttpRequestData>()

    /** Gives "token-1" normally and "token-2" when asked for a fresh one; null = signed out. */
    private class FakeTokens(var signedIn: Boolean = true) : TokenProvider {
        var refreshes = 0
        override suspend fun idToken(forceRefresh: Boolean): String? {
            if (!signedIn) return null
            if (forceRefresh) refreshes++
            return if (forceRefresh) "token-2" else "token-1"
        }
    }

    private fun api(
        tokens: TokenProvider = FakeTokens(),
        baseUrl: String = "http://server.test:8080",
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = FitnessApi(baseUrl, tokens, MockEngine { request -> requests += request; answer(request) }, log = {})

    @Test
    fun success_decodesJson_andSendsTheToken() = runBlocking {
        val api = api { respond("""{"displayName":"Ana","weeklyGoal":3}""", HttpStatusCode.OK, json) }

        val result = api.profile()

        assertEquals(ApiResult.Success(ProfileDto(displayName = "Ana", weeklyGoal = 3)), result)
        val request = requests.single()
        assertEquals("http://server.test:8080/api/profile", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun health_needsNoToken() = runBlocking {
        val api = api(tokens = FakeTokens(signedIn = false)) {
            respond("""{"status":"ok","version":"0.1.0","database":"ok"}""", HttpStatusCode.OK, json)
        }

        assertTrue(api.health() is ApiResult.Success)
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun expiredToken_isRefreshedOnce_andTheRequestRepeated() = runBlocking {
        val tokens = FakeTokens()
        val api = api(tokens) { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer token-1") {
                respond("""{"error":"unauthorized","message":"Sign in first"}""", HttpStatusCode.Unauthorized, json)
            } else {
                respond("{}", HttpStatusCode.OK, json)
            }
        }

        assertEquals(ApiResult.Success(ProfileDto()), api.profile())
        assertEquals(1, tokens.refreshes)
        assertEquals(listOf("Bearer token-1", "Bearer token-2"), requests.map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun rejectedEvenWithAFreshToken_isUnauthorized() = runBlocking {
        val api = api { respond("""{"error":"unauthorized","message":"Sign in first"}""", HttpStatusCode.Unauthorized, json) }

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), api.profile())
        assertEquals(2, requests.size) // tried once more, not forever
    }

    @Test
    fun signedOut_failsWithoutCallingTheServer() = runBlocking {
        val api = api(tokens = FakeTokens(signedIn = false)) { respond("{}", HttpStatusCode.OK, json) }

        assertEquals(ApiResult.Failure(ApiError.NotSignedIn), api.profile())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun noServerAddress_isNotConfigured() = runBlocking {
        val api = api(baseUrl = "") { respond("{}", HttpStatusCode.OK, json) }

        assertEquals(ApiResult.Failure(ApiError.NotConfigured), api.health())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun validationErrors_comeBackWithTheirDetails() = runBlocking {
        val api = api {
            respond(
                """{"error":"validation_failed","message":"The request breaks 1 rule(s)","details":["sets[0].reps must be between 1 and 100"]}""",
                HttpStatusCode.BadRequest,
                json,
            )
        }

        val result = api.uploadWorkout(WorkoutRequest(clientId = 1, startedAt = 1, endedAt = 2))

        assertEquals(ApiResult.Failure(ApiError.Invalid(listOf("sets[0].reps must be between 1 and 100"))), result)
        assertEquals(HttpMethod.Post, requests.single().method)
    }

    @Test
    fun statuses_becomeTypedErrors() = runBlocking {
        var status = HttpStatusCode.NotFound
        val api = api { respond("""{"error":"x","message":"x"}""", status, json) }

        assertEquals(ApiResult.Failure(ApiError.NotFound), api.deleteTemplate(5))
        status = HttpStatusCode.BadGateway
        assertEquals(ApiResult.Failure(ApiError.Server(502)), api.profile())
        status = HttpStatusCode.NoContent
        assertEquals(ApiResult.Success(Unit), api.deleteWorkout(5))
    }

    @Test
    fun noConnection_isOffline() = runBlocking {
        val api = api { throw IOException("Connection refused") }

        assertEquals(ApiResult.Failure(ApiError.Offline), api.profile())
    }

    @Test
    fun unreadableAnswer_isUnexpected() = runBlocking {
        val api = api { respond("<html>not json</html>", HttpStatusCode.OK, json) }

        assertEquals(ApiResult.Failure(ApiError.Unexpected), api.profile())
    }

    @Test
    fun queryParameters() = runBlocking {
        val api = api { respond("[]", HttpStatusCode.OK, json) }

        api.workouts(since = 1_000)

        assertEquals("1000", requests.single().url.parameters["since"])
    }
}

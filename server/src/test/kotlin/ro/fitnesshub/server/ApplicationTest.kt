package ro.fitnesshub.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real [module] in memory (no network) and calls it like the app would. */
class ApplicationTest {

    @Test
    fun health_returnsOk() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertEquals(HealthResponse("ok", SERVER_VERSION), Json.decodeFromString(response.bodyAsText()))
    }

    @Test
    fun unknownRoute_returnsJson404() = testApplication {
        application { module() }

        val response = client.get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = Json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("not_found", body.error)
    }

    @Test
    fun thrownErrors_becomeJsonWithTheRightStatus() = testApplication {
        application {
            module()
            // Test-only routes that fail on purpose.
            routing {
                get("/test/bad") { throw BadRequestException("reps must be 1-100") }
                get("/test/missing") { throw NotFoundException("Template 5 not found") }
                get("/test/crash") { error("secret internal detail") }
            }
        }

        val bad = client.get("/test/bad")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(ErrorResponse("bad_request", "reps must be 1-100"), Json.decodeFromString(bad.bodyAsText()))

        val missing = client.get("/test/missing")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(ErrorResponse("not_found", "Template 5 not found"), Json.decodeFromString(missing.bodyAsText()))

        val crash = client.get("/test/crash")
        assertEquals(HttpStatusCode.InternalServerError, crash.status)
        assertTrue("secret" !in crash.bodyAsText(), "internal details must not reach the client")
    }
}

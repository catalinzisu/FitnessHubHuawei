package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every error the API can answer with, over HTTP, as the app will see it. */
class ErrorResponsesTest {

    @Test
    fun invalidBody_400_listsEveryProblem_andSavesNothing() = apiTest {
        val alice = clientFor("alice")
        val bad = TemplateRequest(
            name = "",
            weekdays = listOf("Funday"),
            exercises = listOf(TemplateExerciseDto("Bench", "chest", 0, 8, 12, 120)),
        )

        val response = alice.post("/api/templates") { setBody(bad) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("validation_failed", error.error)
        assertEquals(3, error.details?.size)
        assertTrue(error.details!!.contains("exercises[0].targetSets must be between 1 and 10"))
        assertTrue(alice.get("/api/templates").body<List<TemplateResponse>>().isEmpty())
    }

    @Test
    fun everyBodyTypeIsValidated() = apiTest {
        val alice = clientFor("alice")

        val profile = alice.put("/api/profile") { setBody(ProfileDto(weeklyGoal = 99)) }
        val workout = alice.post("/api/workouts") { setBody(WorkoutRequest(clientId = 1, startedAt = 2_000, endedAt = 1_000)) }

        assertEquals("validation_failed", profile.body<ErrorResponse>().error)
        assertEquals("validation_failed", workout.body<ErrorResponse>().error)
    }

    @Test
    fun missingRequiredField_400() = apiTest {
        val response = clientFor("alice").post("/api/workouts") { setBody("""{"startedAt": 1000}""") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.body<ErrorResponse>().error)
    }

    @Test
    fun bodyThatIsNotJson_415() = apiTest {
        val response = clientFor("alice").post("/api/templates") {
            contentType(ContentType.Text.Plain)
            setBody("Push A")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertEquals("unsupported_media_type", response.body<ErrorResponse>().error)
    }

    @Test
    fun wrongMethod_405() = apiTest {
        val response = clientFor("alice").patch("/api/profile")

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals("method_not_allowed", response.body<ErrorResponse>().error)
    }

    @Test
    fun upstreamFailure_502_withoutDetails() = apiTest {
        application {
            routing { get("/test/ai") { throw UpstreamException("model quota exceeded for key abc") } }
        }

        val response = clientFor("alice").get("/test/ai")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("upstream_failed", error.error)
        assertTrue("abc" !in error.message) // what went wrong upstream stays in the server log
    }
}

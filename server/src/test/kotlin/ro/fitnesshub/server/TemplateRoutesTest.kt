package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateRoutesTest {

    private val bench = TemplateExerciseDto("Barbell Bench Press", "chest", 3, 8, 12, 120)
    private val fly = TemplateExerciseDto("Cable Fly", "chest", 3, 12, 15, 60)
    private val pushA = TemplateRequest("Push A", listOf("MONDAY", "THURSDAY"), listOf(bench, fly))

    @Test
    fun create_thenReadOneAndAll() = apiTest {
        val alice = clientFor("alice")

        val created = alice.post("/api/templates") { setBody(pushA) }
        assertEquals(HttpStatusCode.Created, created.status)
        val template = created.body<TemplateResponse>()
        assertEquals("Push A", template.name)
        assertEquals(listOf("MONDAY", "THURSDAY"), template.weekdays)
        assertEquals(listOf(bench, fly), template.exercises) // order kept

        assertEquals(template, alice.get("/api/templates/${template.id}").body<TemplateResponse>())
        assertEquals(listOf(template), alice.get("/api/templates").body<List<TemplateResponse>>())
    }

    @Test
    fun update_replacesNameDaysAndExercises() = apiTest {
        val alice = clientFor("alice")
        val id = alice.post("/api/templates") { setBody(pushA) }.body<TemplateResponse>().id

        val response = alice.put("/api/templates/$id") { setBody(TemplateRequest("Push B", listOf("FRIDAY"), listOf(fly))) }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = alice.get("/api/templates/$id").body<TemplateResponse>()
        assertEquals("Push B", updated.name)
        assertEquals(listOf("FRIDAY"), updated.weekdays)
        assertEquals(listOf(fly), updated.exercises)
    }

    @Test
    fun delete_removesIt() = apiTest {
        val alice = clientFor("alice")
        val id = alice.post("/api/templates") { setBody(pushA) }.body<TemplateResponse>().id

        assertEquals(HttpStatusCode.NoContent, alice.delete("/api/templates/$id").status)
        assertEquals(HttpStatusCode.NotFound, alice.get("/api/templates/$id").status)
        assertEquals(HttpStatusCode.NotFound, alice.delete("/api/templates/$id").status)
        assertTrue(alice.get("/api/templates").body<List<TemplateResponse>>().isEmpty())
    }

    @Test
    fun anotherUsersTemplate_looksLikeItDoesNotExist() = apiTest {
        val id = clientFor("alice").post("/api/templates") { setBody(pushA) }.body<TemplateResponse>().id
        val bob = clientFor("bob")

        assertEquals(HttpStatusCode.NotFound, bob.get("/api/templates/$id").status)
        assertEquals(HttpStatusCode.NotFound, bob.put("/api/templates/$id") { setBody(pushA) }.status)
        assertEquals(HttpStatusCode.NotFound, bob.delete("/api/templates/$id").status)
        assertTrue(bob.get("/api/templates").body<List<TemplateResponse>>().isEmpty())
        // Still there for Alice.
        assertEquals(HttpStatusCode.OK, clientFor("alice").get("/api/templates/$id").status)
    }

    @Test
    fun nonNumericId_400() = apiTest {
        val response = clientFor("alice").get("/api/templates/abc")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.body<ErrorResponse>().error)
    }

    @Test
    fun malformedJson_400() = apiTest {
        val response = clientFor("alice").post("/api/templates") { setBody("""{"name": 5""") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

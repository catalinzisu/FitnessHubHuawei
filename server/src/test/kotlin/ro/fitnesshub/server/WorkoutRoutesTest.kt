package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import ro.fitnesshub.api.WorkoutRequest
import ro.fitnesshub.api.WorkoutResponse
import ro.fitnesshub.api.WorkoutSetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutRoutesTest {

    private fun workout(clientId: Long, startedAt: Long) = WorkoutRequest(
        clientId = clientId,
        title = "Push A",
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000,
        avgHr = 120,
        sets = listOf(
            WorkoutSetDto("Barbell Bench Press", "chest", 1, 60.0, 10, isWarmup = true),
            WorkoutSetDto("Barbell Bench Press", "chest", 2, 80.0, 8, rpe = 8.5),
        ),
    )

    @Test
    fun upload_thenList() = apiTest {
        val alice = clientFor("alice")

        val response = alice.post("/api/workouts") { setBody(workout(clientId = 1, startedAt = 1_000)) }

        assertEquals(HttpStatusCode.Created, response.status)
        val stored = response.body<WorkoutResponse>()
        assertEquals(1, stored.clientId)
        assertEquals(120, stored.avgHr)
        assertEquals(workout(1, 1_000).sets, stored.sets)
        assertEquals(listOf(stored), alice.get("/api/workouts").body<List<WorkoutResponse>>())
    }

    @Test
    fun sameWorkoutTwice_isStoredOnce() = apiTest {
        val alice = clientFor("alice")
        val first = alice.post("/api/workouts") { setBody(workout(clientId = 7, startedAt = 1_000)) }

        val again = alice.post("/api/workouts") { setBody(workout(clientId = 7, startedAt = 1_000)) }

        assertEquals(HttpStatusCode.OK, again.status) // not 201: nothing new was created
        assertEquals(first.body<WorkoutResponse>(), again.body<WorkoutResponse>())
        assertEquals(1, alice.get("/api/workouts").body<List<WorkoutResponse>>().size)
    }

    @Test
    fun since_returnsOnlyNewerWorkouts_oldestFirst() = apiTest {
        val alice = clientFor("alice")
        listOf(3_000L, 1_000L, 2_000L).forEachIndexed { i, start ->
            alice.post("/api/workouts") { setBody(workout(clientId = i + 1L, startedAt = start)) }
        }

        val all = alice.get("/api/workouts").body<List<WorkoutResponse>>()
        assertEquals(listOf(1_000L, 2_000L, 3_000L), all.map { it.startedAt })

        val recent = alice.get("/api/workouts?since=2000").body<List<WorkoutResponse>>()
        assertEquals(listOf(2_000L, 3_000L), recent.map { it.startedAt })

        assertEquals(HttpStatusCode.BadRequest, alice.get("/api/workouts?since=yesterday").status)
    }

    @Test
    fun delete_onlyYourOwn() = apiTest {
        val alice = clientFor("alice")
        val id = alice.post("/api/workouts") { setBody(workout(clientId = 1, startedAt = 1_000)) }.body<WorkoutResponse>().id

        assertEquals(HttpStatusCode.NotFound, clientFor("bob").delete("/api/workouts/$id").status)
        assertEquals(HttpStatusCode.NoContent, alice.delete("/api/workouts/$id").status)
        assertTrue(alice.get("/api/workouts").body<List<WorkoutResponse>>().isEmpty())
    }

    @Test
    fun usersDoNotSeeEachOthersWorkouts() = apiTest {
        clientFor("alice").post("/api/workouts") { setBody(workout(clientId = 1, startedAt = 1_000)) }
        // Same clientId for a different user is a different workout.
        val bobs = clientFor("bob").post("/api/workouts") { setBody(workout(clientId = 1, startedAt = 5_000)) }

        assertEquals(HttpStatusCode.Created, bobs.status)
        assertEquals(listOf(5_000L), clientFor("bob").get("/api/workouts").body<List<WorkoutResponse>>().map { it.startedAt })
    }
}

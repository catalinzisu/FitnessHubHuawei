package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** The route around [ro.fitnesshub.server.stats.WeeklyStats]; the maths itself is tested in WeeklyStatsTest. */
class StatsRoutesTest {

    /** Noon (UTC) of a day [daysAgo] days before today. */
    private fun noon(daysAgo: Long): Long =
        LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun workout(clientId: Long, startedAt: Long, kg: Double) = WorkoutRequest(
        clientId = clientId,
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000,
        sets = listOf(WorkoutSetDto("Bench Press", "chest", 1, kg, 8)),
    )

    @Test
    fun weeklyStats_forTheCallingUser() = apiTest {
        val alice = clientFor("alice")
        alice.put("/api/profile") { setBody(ProfileDto(weeklyGoal = 1)) }
        alice.post("/api/workouts") { setBody(workout(1, noon(daysAgo = 14), 80.0)) }
        alice.post("/api/workouts") { setBody(workout(2, noon(daysAgo = 0), 85.0)) }
        clientFor("bob").post("/api/workouts") { setBody(workout(1, noon(daysAgo = 0), 200.0)) }

        val response = alice.get("/api/stats/weekly?weeks=3&tz=UTC")

        assertEquals(HttpStatusCode.OK, response.status)
        val stats = response.body<WeeklyStatsResponse>()
        assertEquals(1, stats.weeklyGoal)
        assertEquals(3, stats.weeks.size)
        val thisWeek = stats.weeks.last()
        assertEquals(1, thisWeek.workouts) // Bob's workout doesn't count
        assertEquals(true, thisWeek.goalMet)
        assertEquals(85.0 * 8, thisWeek.totalVolumeKg)
        assertEquals(85.0, thisWeek.personalRecords.single().weightKg)
    }

    @Test
    fun defaultsTo4Weeks() = apiTest {
        val stats = clientFor("alice").get("/api/stats/weekly").body<WeeklyStatsResponse>()

        assertEquals(4, stats.weeks.size)
    }

    @Test
    fun badParameters_400() = apiTest {
        val alice = clientFor("alice")

        for (query in listOf("weeks=0", "weeks=53", "weeks=many", "tz=Mars/Olympus")) {
            val response = alice.get("/api/stats/weekly?$query")
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals("bad_request", response.body<ErrorResponse>().error)
        }
    }

    @Test
    fun withoutSignIn_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, clientFor(null).get("/api/stats/weekly").status)
    }
}

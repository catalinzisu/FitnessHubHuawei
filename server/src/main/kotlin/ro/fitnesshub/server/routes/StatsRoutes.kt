package ro.fitnesshub.server.routes

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ro.fitnesshub.server.db.ProfileRepository
import ro.fitnesshub.server.db.UserRepository
import ro.fitnesshub.server.db.WorkoutRepository
import ro.fitnesshub.server.stats.WeeklyStats
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** Weeks returned when the app doesn't ask for a number. */
private const val DEFAULT_WEEKS = 4

/** About a year; keeps the answer small. */
private const val MAX_WEEKS = 52

/**
 * GET /api/stats/weekly?weeks=4&tz=Europe/Bucharest
 * weeks: 1–52 (default 4). tz: the phone's time zone, which decides on which day a workout
 * counts (default UTC).
 */
fun Route.statsRoutes(users: UserRepository, profiles: ProfileRepository, workouts: WorkoutRepository) {
    get("/stats/weekly") {
        val uid = call.currentUser(users)
        val params = call.request.queryParameters

        val weeks = params["weeks"]?.let { text ->
            text.toIntOrNull()?.takeIf { it in 1..MAX_WEEKS }
                ?: throw BadRequestException("'weeks' must be a number from 1 to $MAX_WEEKS")
        } ?: DEFAULT_WEEKS
        val zone = params["tz"]?.let { text ->
            try {
                ZoneId.of(text)
            } catch (e: DateTimeException) {
                throw BadRequestException("'tz' is not a time zone: $text")
            }
        } ?: ZoneId.of("UTC")

        // All workouts are needed, not just the last weeks: a record means "better than ever before".
        val stats = WeeklyStats.compute(
            workouts = workouts.list(uid, since = null),
            weeks = weeks,
            today = LocalDate.now(zone),
            zone = zone,
            weeklyGoal = profiles.get(uid).weeklyGoal,
        )
        call.respond(stats)
    }
}

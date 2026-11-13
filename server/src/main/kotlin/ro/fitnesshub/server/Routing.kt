package ro.fitnesshub.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import ro.fitnesshub.server.db.AppDatabase
import ro.fitnesshub.server.db.ProfileRepository
import ro.fitnesshub.server.db.TemplateRepository
import ro.fitnesshub.server.db.UserRepository
import ro.fitnesshub.server.db.WorkoutRepository
import ro.fitnesshub.server.routes.profileRoutes
import ro.fitnesshub.server.routes.statsRoutes
import ro.fitnesshub.server.routes.templateRoutes
import ro.fitnesshub.server.routes.workoutRoutes

const val SERVER_VERSION = "0.1.0"

/**
 * All HTTP routes. Routes only read the request, call a repository and send the answer;
 * the SQL lives in the repositories (db package) and the calculations in the stats package.
 */
fun Application.configureRouting(database: AppDatabase) {
    val users = UserRepository(database)
    val profiles = ProfileRepository(database)
    val templates = TemplateRepository(database)
    val workouts = WorkoutRepository(database)

    routing {
        // Lets the app (and us) check that the server and its database are up. No login needed.
        get("/health") {
            val databaseUp = database.isReachable()
            call.respond(
                if (databaseUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResponse(
                    status = if (databaseUp) "ok" else "degraded",
                    version = SERVER_VERSION,
                    database = if (databaseUp) "ok" else "down",
                ),
            )
        }

        // Everything under /api belongs to a user.
        route("/api") {
            profileRoutes(users, profiles)
            templateRoutes(users, templates)
            workoutRoutes(users, workouts)
            statsRoutes(users, profiles, workouts)
        }
    }
}

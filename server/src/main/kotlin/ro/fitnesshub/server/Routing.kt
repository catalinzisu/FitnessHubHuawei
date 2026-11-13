package ro.fitnesshub.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import ro.fitnesshub.server.db.AppDatabase

const val SERVER_VERSION = "0.1.0"

/** All HTTP routes. The /api routes are added in the next steps (B3). */
fun Application.configureRouting(database: AppDatabase) {
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
    }
}

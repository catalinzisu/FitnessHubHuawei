package ro.fitnesshub.server

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

const val SERVER_VERSION = "0.1.0"

/** All HTTP routes. The /api routes are added in the next steps (B3). */
fun Application.configureRouting() {
    routing {
        // Lets the app (and us) check that the server is up. No login needed.
        get("/health") {
            call.respond(HealthResponse(status = "ok", version = SERVER_VERSION))
        }
    }
}

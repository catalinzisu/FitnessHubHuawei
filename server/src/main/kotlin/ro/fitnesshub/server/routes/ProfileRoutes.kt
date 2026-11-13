package ro.fitnesshub.server.routes

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import ro.fitnesshub.server.ProfileDto
import ro.fitnesshub.server.db.ProfileRepository
import ro.fitnesshub.server.db.UserRepository

/** GET /api/profile and PUT /api/profile. */
fun Route.profileRoutes(users: UserRepository, profiles: ProfileRepository) {
    route("/profile") {
        get {
            val uid = call.currentUser(users)
            call.respond(profiles.get(uid))
        }

        put {
            val uid = call.currentUser(users)
            val body = call.receive<ProfileDto>()
            call.respond(profiles.put(uid, body))
        }
    }
}

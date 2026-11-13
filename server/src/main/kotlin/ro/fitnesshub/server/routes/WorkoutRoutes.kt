package ro.fitnesshub.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ro.fitnesshub.api.WorkoutRequest
import ro.fitnesshub.server.db.UserRepository
import ro.fitnesshub.server.db.WorkoutRepository

/** Finished workouts uploaded by the app. They are not edited on the server, only added or deleted. */
fun Route.workoutRoutes(users: UserRepository, workouts: WorkoutRepository) {
    route("/workouts") {
        // GET /api/workouts?since=<epoch millis>  (without since: all of them)
        get {
            val uid = call.currentUser(users)
            val since = call.request.queryParameters["since"]?.let {
                it.toLongOrNull() ?: throw BadRequestException("'since' must be a time in epoch milliseconds")
            }
            call.respond(workouts.list(uid, since))
        }

        // 201 when the workout is new, 200 when it had already been uploaded.
        post {
            val uid = call.currentUser(users)
            val body = call.receive<WorkoutRequest>()
            val result = workouts.upload(uid, body)
            call.respond(if (result.created) HttpStatusCode.Created else HttpStatusCode.OK, result.workout)
        }

        delete("/{id}") {
            val uid = call.currentUser(users)
            val id = call.longParameter("id")
            if (!workouts.delete(uid, id)) throw NotFoundException("Workout $id not found")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

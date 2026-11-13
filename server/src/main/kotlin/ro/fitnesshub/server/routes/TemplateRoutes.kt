package ro.fitnesshub.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import ro.fitnesshub.api.TemplateRequest
import ro.fitnesshub.server.db.TemplateRepository
import ro.fitnesshub.server.db.UserRepository

/**
 * Training days: full CRUD.
 * A template of another user answers 404, exactly like one that doesn't exist,
 * so nobody can find out which ids other people have.
 */
fun Route.templateRoutes(users: UserRepository, templates: TemplateRepository) {
    route("/templates") {
        get {
            val uid = call.currentUser(users)
            call.respond(templates.list(uid))
        }

        post {
            val uid = call.currentUser(users)
            val body = call.receive<TemplateRequest>()
            call.respond(HttpStatusCode.Created, templates.create(uid, body))
        }

        get("/{id}") {
            val uid = call.currentUser(users)
            val id = call.longParameter("id")
            call.respond(templates.get(uid, id) ?: throw NotFoundException("Template $id not found"))
        }

        put("/{id}") {
            val uid = call.currentUser(users)
            val id = call.longParameter("id")
            val body = call.receive<TemplateRequest>()
            call.respond(templates.update(uid, id, body) ?: throw NotFoundException("Template $id not found"))
        }

        delete("/{id}") {
            val uid = call.currentUser(users)
            val id = call.longParameter("id")
            if (!templates.delete(uid, id)) throw NotFoundException("Template $id not found")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

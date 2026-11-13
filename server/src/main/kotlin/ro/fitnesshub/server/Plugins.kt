package ro.fitnesshub.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** JSON in and out: request bodies become @Serializable classes and responses are written as JSON. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true // an older app version may send fields the server doesn't know
                explicitNulls = false // leave null fields out of responses
            },
        )
    }
}

/** One log line per request: method, path and status. */
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()}"
        }
    }
}

/**
 * Turns exceptions into JSON errors, so a route can simply `throw` and the app always gets
 * `{"error": "...", "message": "..."}` with the right status code instead of an HTML page.
 * Rule for routes: report errors by throwing (BadRequestException, NotFoundException, ...),
 * never by responding with an error status yourself.
 */
fun Application.configureErrors() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            // Also thrown by Ktor itself when a JSON body can't be read.
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Invalid request"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message ?: "Not found"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error on ${call.request.path()}", cause)
            // No details: internal messages could leak how the server works.
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal", "Something went wrong on the server"))
        }
        // A URL that matches no route.
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "No route for ${call.request.path()}"))
        }
    }
}

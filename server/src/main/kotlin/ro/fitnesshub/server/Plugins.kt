package ro.fitnesshub.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
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
                encodeDefaults = true // write e.g. "isWarmup": false instead of leaving it out
                explicitNulls = false // leave null fields out of responses
            },
        )
    }
}

/**
 * Checks every request body with the rules in [Validation] as soon as a route calls
 * `call.receive<...>()`. Invalid bodies never reach the route: they become a 400 listing every problem.
 */
fun Application.configureValidation() {
    install(RequestValidation) {
        validate<ProfileDto> { toResult(Validation.profile(it)) }
        validate<TemplateRequest> { toResult(Validation.template(it)) }
        validate<WorkoutRequest> { toResult(Validation.workout(it)) }
    }
}

private fun toResult(problems: List<String>): ValidationResult =
    if (problems.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(problems)

/** One log line per request: method, path and status. */
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()}"
        }
    }
}

/** Thrown when a service the server depends on (the AI model, phase D) fails. Becomes a 502. */
class UpstreamException(message: String) : RuntimeException(message)

/**
 * Turns exceptions into JSON errors, so a route can simply `throw` and the app always gets
 * `{"error": "...", "message": "..."}` with the right status code instead of an HTML page.
 * Rule for routes: report errors by throwing (BadRequestException, NotFoundException, ...),
 * never by responding with an error status yourself.
 *
 *  400 bad_request         – malformed JSON, wrong parameter
 *  400 validation_failed   – JSON is fine but breaks a rule; "details" lists every problem
 *  401 unauthorized        – no (valid) sign-in
 *  404 not_found           – no such route, or no such item for this user
 *  405 method_not_allowed  – e.g. PATCH on a route that has no PATCH
 *  415 unsupported_media_type – body that isn't JSON
 *  502 upstream_failed     – the AI service didn't answer properly
 *  500 internal            – a bug; details only go to the server log
 */
fun Application.configureErrors() {
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("validation_failed", "The request breaks ${cause.reasons.size} rule(s)", cause.reasons),
            )
        }
        // Ktor throws the first when the content type is unknown and the second when it has
        // no way to read that type (e.g. text/plain) into the class the route wants.
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("unsupported_media_type", "Send the body as application/json"))
        }
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("unsupported_media_type", "Send the body as application/json"))
        }
        exception<BadRequestException> { call, cause ->
            // Also thrown by Ktor itself when a JSON body can't be read.
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Invalid request"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message ?: "Sign in first"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message ?: "Not found"))
        }
        exception<UpstreamException> { call, cause ->
            call.application.environment.log.warn("Upstream failure on ${call.request.path()}: ${cause.message}")
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("upstream_failed", "The AI service is not available right now"))
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
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ErrorResponse("method_not_allowed", "${call.request.httpMethod.value} is not supported on ${call.request.path()}"),
            )
        }
    }
}

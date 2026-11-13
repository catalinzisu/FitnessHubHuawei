package ro.fitnesshub.server

import io.ktor.server.application.ApplicationCall

/** Thrown when a request doesn't say (or can't prove) who is making it. Becomes a 401 response. */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * TEMPORARY, until step C4: the user is simply named in this header, with no proof.
 * C4 replaces [userId] with the user id from a verified Firebase login token; the routes
 * and repositories don't change, because they only ever see the id this function returns.
 */
const val USER_HEADER = "X-User-Id"

/** The id of the user making the request, or a 401 error. */
fun ApplicationCall.userId(): String =
    request.headers[USER_HEADER]?.takeIf { it.isNotBlank() }
        ?: throw UnauthorizedException("Sign in first")

package ro.fitnesshub.server.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import ro.fitnesshub.server.db.UserRepository
import ro.fitnesshub.server.userId

/** The id of the user making the request (401 if unknown). Creates their row on first use. */
suspend fun ApplicationCall.currentUser(users: UserRepository): String {
    val uid = userId()
    users.ensure(uid)
    return uid
}

/** A numeric path parameter such as {id}; 400 if it isn't a number. */
fun ApplicationCall.longParameter(name: String): Long =
    parameters[name]?.toLongOrNull() ?: throw BadRequestException("'$name' must be a number")

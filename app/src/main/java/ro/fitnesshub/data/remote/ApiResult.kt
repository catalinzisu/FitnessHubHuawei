package ro.fitnesshub.data.remote

/**
 * Outcome of a call to our server. Calls never throw: every failure becomes an [ApiError],
 * so a screen or a background job always knows what happened and can tell the user.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Why a server call failed, in terms the UI can explain. */
sealed interface ApiError {
    /** This build has no server address (server.url missing from local.properties). */
    data object NotConfigured : ApiError

    /** Nobody is signed in, so there is no token to send; the request isn't even made. */
    data object NotSignedIn : ApiError

    /** The server rejected the sign-in token, even after asking Firebase for a fresh one. */
    data object Unauthorized : ApiError

    /** No connection, or the server isn't running / can't be reached. */
    data object Offline : ApiError

    /** The server didn't answer in time. */
    data object Timeout : ApiError

    /** The server refused the data (400); [details] lists the broken rules. */
    data class Invalid(val details: List<String>) : ApiError

    /** The item doesn't exist (or belongs to someone else). */
    data object NotFound : ApiError

    /** The server failed (5xx), or answered with a status the app doesn't expect. */
    data class Server(val status: Int) : ApiError

    /** The answer couldn't be read (e.g. JSON from a different server version). */
    data object Unexpected : ApiError
}

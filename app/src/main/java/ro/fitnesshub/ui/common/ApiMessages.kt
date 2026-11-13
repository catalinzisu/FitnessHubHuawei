package ro.fitnesshub.ui.common

import ro.fitnesshub.R
import ro.fitnesshub.data.remote.ApiError

/** What to tell the user when a server call fails. Shared by every screen that talks to the server. */
fun ApiError.toMessage(): UiMessage = when (this) {
    ApiError.NotConfigured -> UiMessage(R.string.api_error_not_configured)
    ApiError.NotSignedIn -> UiMessage(R.string.api_error_not_signed_in)
    ApiError.Unauthorized -> UiMessage(R.string.api_error_unauthorized)
    ApiError.Offline -> UiMessage(R.string.api_error_offline)
    ApiError.Timeout -> UiMessage(R.string.api_error_timeout)
    is ApiError.Invalid -> UiMessage(R.string.api_error_invalid, listOf(details.firstOrNull().orEmpty()))
    ApiError.NotFound -> UiMessage(R.string.api_error_not_found)
    is ApiError.Server -> UiMessage(R.string.api_error_server, listOf(status))
    ApiError.Unexpected -> UiMessage(R.string.api_error_unexpected)
}

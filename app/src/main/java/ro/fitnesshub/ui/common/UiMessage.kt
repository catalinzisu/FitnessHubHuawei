package ro.fitnesshub.ui.common

import androidx.annotation.StringRes

/** A one-off message for a snackbar, resolved to text in the UI. */
data class UiMessage(@param:StringRes val text: Int, val args: List<Any> = emptyList())

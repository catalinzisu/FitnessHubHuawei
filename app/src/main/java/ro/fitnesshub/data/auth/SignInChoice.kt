package ro.fitnesshub.data.auth

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers that the user chose "Continue without account" on the sign-in screen, so the app
 * opens on Today from then on. Signing out clears it, so the sign-in screen comes back.
 * A single flag, so plain SharedPreferences is enough.
 */
class SignInChoice(context: Context) {

    private val prefs = context.getSharedPreferences("sign_in", Context.MODE_PRIVATE)

    var continuedWithoutAccount: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    companion object {
        private const val KEY = "continued_without_account"

        /**
         * Whether the app opens on the sign-in screen: only when sign-in exists in this build,
         * nobody is signed in and the user hasn't chosen to go on without an account.
         */
        fun startsAtSignIn(configured: Boolean, signedIn: Boolean, continuedWithoutAccount: Boolean): Boolean =
            configured && !signedIn && !continuedWithoutAccount
    }
}

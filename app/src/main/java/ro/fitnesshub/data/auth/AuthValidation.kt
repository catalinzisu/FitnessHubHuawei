package ro.fitnesshub.data.auth

/** What is wrong with one form field. The UI turns it into text (ui/auth/AuthTexts.kt). */
enum class FieldError { Required, InvalidEmail, PasswordTooShort, PasswordNeedsLetterAndDigit, PasswordsDontMatch }

/**
 * Checks the sign-in forms before anything is sent to Firebase, so the user gets an instant,
 * specific message. Pure Kotlin, unit-tested (AuthValidationTest).
 */
object AuthValidation {

    /** Firebase accepts 6; 8 with a letter and a digit is a sensible minimum for a new password. */
    const val MIN_PASSWORD_LENGTH = 8

    /** Something@something.something, no spaces. Firebase does the full check. */
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(text: String): FieldError? = when {
        text.isBlank() -> FieldError.Required
        !EMAIL.matches(text.trim()) -> FieldError.InvalidEmail
        else -> null
    }

    /** Signing in: only "not empty", because old accounts may have older, shorter passwords. */
    fun existingPassword(text: String): FieldError? = if (text.isEmpty()) FieldError.Required else null

    /** Creating an account. */
    fun newPassword(text: String): FieldError? = when {
        text.isEmpty() -> FieldError.Required
        text.length < MIN_PASSWORD_LENGTH -> FieldError.PasswordTooShort
        text.none { it.isLetter() } || text.none { it.isDigit() } -> FieldError.PasswordNeedsLetterAndDigit
        else -> null
    }

    fun confirmation(password: String, confirm: String): FieldError? = when {
        confirm.isEmpty() -> FieldError.Required
        confirm != password -> FieldError.PasswordsDontMatch
        else -> null
    }
}

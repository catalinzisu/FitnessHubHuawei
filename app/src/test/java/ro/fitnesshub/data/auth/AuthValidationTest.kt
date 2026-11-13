package ro.fitnesshub.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun email() {
        assertNull(AuthValidation.email("ana@example.com"))
        assertNull(AuthValidation.email("  ana.pop+gym@mail.example.ro  ")) // spaces around are trimmed
        assertEquals(FieldError.Required, AuthValidation.email("   "))
        assertEquals(FieldError.InvalidEmail, AuthValidation.email("ana"))
        assertEquals(FieldError.InvalidEmail, AuthValidation.email("ana@example"))
        assertEquals(FieldError.InvalidEmail, AuthValidation.email("ana pop@example.com"))
        assertEquals(FieldError.InvalidEmail, AuthValidation.email("@example.com"))
    }

    @Test
    fun existingPassword_onlyNeedsSomething() {
        assertNull(AuthValidation.existingPassword("abc"))
        assertEquals(FieldError.Required, AuthValidation.existingPassword(""))
    }

    @Test
    fun newPassword() {
        assertNull(AuthValidation.newPassword("bench100"))
        assertEquals(FieldError.Required, AuthValidation.newPassword(""))
        assertEquals(FieldError.PasswordTooShort, AuthValidation.newPassword("ab12"))
        assertEquals(FieldError.PasswordNeedsLetterAndDigit, AuthValidation.newPassword("benchpress"))
        assertEquals(FieldError.PasswordNeedsLetterAndDigit, AuthValidation.newPassword("12345678"))
    }

    @Test
    fun confirmation() {
        assertNull(AuthValidation.confirmation("bench100", "bench100"))
        assertEquals(FieldError.Required, AuthValidation.confirmation("bench100", ""))
        assertEquals(FieldError.PasswordsDontMatch, AuthValidation.confirmation("bench100", "bench101"))
    }
}

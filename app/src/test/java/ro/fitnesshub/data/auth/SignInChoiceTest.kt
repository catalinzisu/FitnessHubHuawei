package ro.fitnesshub.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which screen the app opens on. */
class SignInChoiceTest {

    @Test
    fun opensOnSignIn_onlyForSomeoneWhoHasNotChosenYet() {
        assertTrue(SignInChoice.startsAtSignIn(configured = true, signedIn = false, continuedWithoutAccount = false))
    }

    @Test
    fun opensOnToday_whenSignedIn_orWhenTheUserSkipped() {
        assertFalse(SignInChoice.startsAtSignIn(configured = true, signedIn = true, continuedWithoutAccount = false))
        assertFalse(SignInChoice.startsAtSignIn(configured = true, signedIn = false, continuedWithoutAccount = true))
    }

    @Test
    fun opensOnToday_whenTheBuildHasNoFirebase() {
        assertFalse(SignInChoice.startsAtSignIn(configured = false, signedIn = false, continuedWithoutAccount = false))
    }
}

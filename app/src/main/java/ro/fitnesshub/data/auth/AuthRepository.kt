package ro.fitnesshub.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import ro.fitnesshub.util.AppLog

/** The signed-in account, as the rest of the app sees it (no Firebase types outside this package). */
data class AuthUser(val uid: String, val email: String?)

/** Why a sign-in action failed, in terms the UI can explain. */
enum class AuthError { NotConfigured, InvalidCredentials, EmailInUse, WeakPassword, InvalidEmail, Network, TooManyRequests, Unknown }

/** Gives the current user's Firebase ID token; the API client needs nothing else from sign-in. */
fun interface TokenProvider {
    /** Null when nobody is signed in. [forceRefresh] asks Firebase for a new token even if the old one looks valid. */
    suspend fun idToken(forceRefresh: Boolean): String?
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * Sign-in through Firebase Authentication (email + password). The only class that talks to Firebase.
 * [auth] is null when the build has no google-services.json; every action then fails with
 * [AuthError.NotConfigured] instead of crashing.
 */
class AuthRepository(private val auth: FirebaseAuth?) : TokenProvider {

    val isConfigured: Boolean get() = auth != null

    /** The current account, or null when signed out. Firebase pushes every change (sign-in, sign-out). */
    val currentUser: Flow<AuthUser?> = if (auth == null) {
        flowOf(null)
    } else {
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
            auth.addAuthStateListener(listener) // also reports the current state right away
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    }

    /** The account right now, without waiting; used to pick the first screen. */
    fun currentUserNow(): AuthUser? = auth?.currentUser?.toAuthUser()

    suspend fun signIn(email: String, password: String): AuthResult =
        call("Sign-in") { it.signInWithEmailAndPassword(email.trim(), password).await() }

    /** Creates the account and signs it in. */
    suspend fun register(email: String, password: String): AuthResult =
        call("Registration") { it.createUserWithEmailAndPassword(email.trim(), password).await() }

    /**
     * Asks Firebase to email a reset link. It succeeds even when no account uses the address,
     * so nobody can use this form to find out who has an account.
     */
    suspend fun sendPasswordReset(email: String): AuthResult =
        call("Password reset") { it.sendPasswordResetEmail(email.trim()).await() }

    fun signOut() {
        auth?.signOut()
        AppLog.i(TAG, "Signed out")
    }

    /**
     * A Firebase ID token (a signed JWT, valid one hour) proving who the user is; the app sends it
     * to our server, which checks it. Firebase keeps it cached and renews it when it is about to
     * expire, or right away with [forceRefresh].
     */
    override suspend fun idToken(forceRefresh: Boolean): String? =
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    /** Runs a Firebase action and turns its outcome into an [AuthResult]. Emails are never logged. */
    private suspend fun call(action: String, block: suspend (FirebaseAuth) -> Unit): AuthResult {
        val auth = auth ?: return AuthResult.Failure(AuthError.NotConfigured)
        return try {
            block(auth)
            AppLog.i(TAG, "$action OK")
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = errorOf(e)
            AppLog.w(TAG, "$action failed: $error")
            AuthResult.Failure(error)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(uid, email)

    companion object {
        private const val TAG = "Auth"

        /** Maps Firebase's exceptions to [AuthError]. The order matters: subclasses first. */
        fun errorOf(e: Exception): AuthError = when (e) {
            is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword // a kind of InvalidCredentials
            is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
            // "No such user" and "wrong password" get the same message, so the form doesn't reveal
            // which emails have an account.
            is FirebaseAuthInvalidUserException -> AuthError.InvalidCredentials
            is FirebaseAuthInvalidCredentialsException ->
                if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.InvalidCredentials
            is FirebaseNetworkException -> AuthError.Network
            is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
            else -> AuthError.Unknown
        }
    }
}

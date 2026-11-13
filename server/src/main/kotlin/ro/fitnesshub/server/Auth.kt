package ro.fitnesshub.server

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import ro.fitnesshub.api.ErrorResponse
import java.net.URI
import java.util.concurrent.TimeUnit

// Who is calling? The app signs in with Firebase Authentication and sends the Firebase ID token
// in every /api request:  Authorization: Bearer <token>
// The token is a JWT signed by Google. The server never sees a password: it only checks that the
// token is genuine, meant for our Firebase project and not expired, and then trusts its user id.

/** Name of the authentication setup; routes use it in `authenticate(FIREBASE_AUTH) { ... }`. */
const val FIREBASE_AUTH = "firebase"

/** Thrown when a request doesn't prove who is making it. Becomes a 401 response. */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * What a valid Firebase ID token must look like for our project.
 * [jwkProvider] gives Google's public keys by key id; tests pass their own keys instead.
 */
class FirebaseAuthConfig(val projectId: String, val jwkProvider: JwkProvider) {

    /** Firebase writes this in the token's "iss" claim. */
    val issuer: String get() = "https://securetoken.google.com/$projectId"

    companion object {
        /** Google's public keys for Firebase ID tokens, as a JSON Web Key Set. */
        private const val GOOGLE_KEYS_URL =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"

        /** The real setup: keys downloaded from Google and kept for a day (Google rotates them every few days). */
        fun forProject(projectId: String) = FirebaseAuthConfig(
            projectId = projectId,
            jwkProvider = JwkProviderBuilder(URI(GOOGLE_KEYS_URL).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES) // a flood of fake key ids can't make us spam Google
                .build(),
        )
    }
}

/**
 * Installs the JWT check. For a request to pass, the token must:
 *  - be signed (RS256) with one of Google's current keys, picked by the "kid" in its header;
 *  - have issuer https://securetoken.google.com/<project> and audience <project>;
 *  - not be expired (30 s of clock difference allowed);
 *  - name a user ("sub" claim, the Firebase uid).
 */
fun Application.configureAuthentication(config: FirebaseAuthConfig) {
    install(Authentication) {
        jwt(FIREBASE_AUTH) {
            realm = "fitness-hub"
            verifier(config.jwkProvider, config.issuer) {
                withAudience(config.projectId)
                acceptLeeway(30)
            }
            validate { credential ->
                if (credential.payload.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
            // Missing, malformed, forged or expired token. The reason is not told to the caller.
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Sign in first"))
            }
        }
    }
}

/** The Firebase user id of the caller, from the verified token. Only valid inside `authenticate`. */
fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()?.subject ?: throw UnauthorizedException("Sign in first")

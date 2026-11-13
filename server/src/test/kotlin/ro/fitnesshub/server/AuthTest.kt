package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Firebase ID token check: only genuine, current tokens for our project get in. */
class AuthTest {

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.statusWith(token: String?) =
        clientWithToken(token).get("/api/profile").status

    @Test
    fun validToken_200() = apiTest {
        assertEquals(HttpStatusCode.OK, statusWith(TestTokens.token("alice")))
    }

    @Test
    fun noToken_401_withJsonError() = apiTest {
        val response = clientWithToken(null).get("/api/profile")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("unauthorized", response.body<ErrorResponse>().error)
    }

    @Test
    fun expiredToken_401() = apiTest {
        // Expired 2 minutes ago: more than the 30 s allowed for clock differences.
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("alice", expiresInSeconds = -120)))
    }

    @Test
    fun tokenForAnotherFirebaseProject_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("alice", audience = "someone-elses-app")))
    }

    @Test
    fun tokenFromAnotherIssuer_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("alice", issuer = "https://evil.example.com")))
    }

    @Test
    fun forgedSignature_401() = apiTest {
        // Right key id, but signed with a key that isn't Google's (here: the test's "Google").
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("alice", signWith = TestTokens.untrusted)))
    }

    @Test
    fun unknownKeyId_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("alice", keyId = "made-up")))
    }

    @Test
    fun emptyUserId_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, statusWith(TestTokens.token("")))
    }

    @Test
    fun garbage_401() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, statusWith("not-a-jwt"))
        // An unsigned token ("alg": "none") must never be accepted.
        val unsigned = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJhbGljZSJ9."
        assertEquals(HttpStatusCode.Unauthorized, statusWith(unsigned))
    }

    @Test
    fun oldUserHeader_isIgnored() = apiTest {
        // The temporary X-User-Id header from before is gone: naming a user proves nothing.
        val response = clientWithToken(null).get("/api/profile") { header("X-User-Id", "alice") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun theUserComesFromTheToken() = apiTest {
        clientFor("alice").put("/api/profile") { setBody(ProfileDto(displayName = "Alice")) }

        assertEquals("Alice", clientFor("alice").get("/api/profile").body<ProfileDto>().displayName)
        assertEquals(null, clientFor("bob").get("/api/profile").body<ProfileDto>().displayName)
    }

    @Test
    fun healthNeedsNoToken() = apiTest {
        assertEquals(HttpStatusCode.OK, clientWithToken(null).get("/health").status)
    }
}

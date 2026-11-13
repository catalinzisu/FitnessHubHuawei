package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import ro.fitnesshub.api.ErrorResponse
import ro.fitnesshub.api.ProfileDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRoutesTest {

    @Test
    fun newUser_getsEmptyProfile() = apiTest {
        val response = clientFor("alice").get("/api/profile")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ProfileDto(), response.body<ProfileDto>())
    }

    @Test
    fun put_savesAndReplacesTheProfile() = apiTest {
        val alice = clientFor("alice")

        val saved = alice.put("/api/profile") { setBody(ProfileDto("Alice", 170, 62.5, 3)) }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals(ProfileDto("Alice", 170, 62.5, 3), alice.get("/api/profile").body<ProfileDto>())

        // PUT replaces everything: fields left out become empty.
        alice.put("/api/profile") { setBody(ProfileDto(weeklyGoal = 4)) }
        assertEquals(ProfileDto(weeklyGoal = 4), alice.get("/api/profile").body<ProfileDto>())
    }

    @Test
    fun profiles_areSeparatePerUser() = apiTest {
        clientFor("alice").put("/api/profile") { setBody(ProfileDto(displayName = "Alice")) }

        assertEquals(ProfileDto(), clientFor("bob").get("/api/profile").body<ProfileDto>())
    }

    @Test
    fun withoutSignIn_401() = apiTest {
        val response = clientFor(null).get("/api/profile")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("unauthorized", response.body<ErrorResponse>().error)
    }
}

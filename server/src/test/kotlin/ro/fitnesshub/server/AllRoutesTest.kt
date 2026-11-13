package ro.fitnesshub.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import ro.fitnesshub.server.db.AppDatabase
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rules that must hold for every route, checked on all of them at once. */
class AllRoutesTest {

    /** Every /api route (method and path). Add new routes here. */
    private val apiRoutes = listOf(
        HttpMethod.Get to "/api/profile",
        HttpMethod.Put to "/api/profile",
        HttpMethod.Get to "/api/templates",
        HttpMethod.Post to "/api/templates",
        HttpMethod.Get to "/api/templates/1",
        HttpMethod.Put to "/api/templates/1",
        HttpMethod.Delete to "/api/templates/1",
        HttpMethod.Get to "/api/workouts",
        HttpMethod.Post to "/api/workouts",
        HttpMethod.Delete to "/api/workouts/1",
        HttpMethod.Get to "/api/stats/weekly",
    )

    @Test
    fun everyApiRoute_needsSignIn() = apiTest {
        val anonymous = clientFor(null)

        for ((method, path) in apiRoutes) {
            val response = anonymous.request(path) {
                this.method = method
                setBody("{}")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status, "$method $path")
            assertEquals("unauthorized", response.body<ErrorResponse>().error, "$method $path")
        }
    }

    @Test
    fun everyIdInThePath_mustBeANumber() = apiTest {
        val alice = clientFor("alice")

        for ((method, path) in apiRoutes.filter { it.second.endsWith("/1") }) {
            val response = alice.request(path.replace("/1", "/abc")) {
                this.method = method
                setBody(TemplateRequest("Push A"))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "$method $path")
        }
    }

    @Test
    fun health_whenTheDatabaseIsDown_503() = testApplication {
        // Two connections to the same in-memory database: the server uses one, the test
        // shuts the database down through the other. IFEXISTS stops H2 from quietly
        // creating a new, empty database when the server reconnects.
        val name = "health-${System.nanoTime()}"
        val owner = AppDatabase.inMemory(name)
        application { module(AppDatabase("jdbc:h2:mem:$name;IFEXISTS=TRUE"), TestTokens.config) }
        val client = clientFor(null)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        try {
            owner.query { exec("SHUTDOWN") }
        } catch (expected: SQLException) {
            // The connection that runs SHUTDOWN closes with the database, so its commit fails.
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("down", response.body<HealthResponse>().database)
    }
}

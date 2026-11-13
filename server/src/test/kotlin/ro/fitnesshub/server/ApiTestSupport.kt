package ro.fitnesshub.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ro.fitnesshub.server.db.AppDatabase

/** A fresh in-memory database for one test. */
fun testDatabase(): AppDatabase = AppDatabase.inMemory("test-${System.nanoTime()}")

/** Starts the real server module (fresh database, test token keys) and runs [block] against it. */
fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module(testDatabase(), TestTokens.config) }
    block()
}

/**
 * A client that sends and reads JSON, signed in as [uid] with a valid test token like the app
 * sends (`Authorization: Bearer <Firebase ID token>`). [uid] = null: not signed in.
 */
fun ApplicationTestBuilder.clientFor(uid: String?): HttpClient = clientWithToken(uid?.let { TestTokens.token(it) })

/** Same, with any token (or none), for testing bad tokens. */
fun ApplicationTestBuilder.clientWithToken(token: String?): HttpClient = createClient {
    install(ContentNegotiation) { json() }
    defaultRequest {
        contentType(ContentType.Application.Json)
        if (token != null) bearerAuth(token)
    }
}

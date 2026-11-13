package ro.fitnesshub.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ro.fitnesshub.server.db.AppDatabase

/** Starts the real server module on a fresh in-memory database and runs [block] against it. */
fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module(AppDatabase.inMemory("api-test-${System.nanoTime()}")) }
    block()
}

/** A client that sends and reads JSON, signed in as [uid] (null = not signed in). */
fun ApplicationTestBuilder.clientFor(uid: String?): HttpClient = createClient {
    install(ContentNegotiation) { json() }
    defaultRequest {
        contentType(ContentType.Application.Json)
        if (uid != null) headers.append(USER_HEADER, uid)
    }
}

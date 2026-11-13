package ro.fitnesshub.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ro.fitnesshub.server.db.AppDatabase

/**
 * Entry point of `./gradlew :server:run`. Settings come from environment variables:
 * FIREBASE_PROJECT_ID (required; `run` also reads it from local.properties), PORT (default 8080)
 * and DB_URL (default: a file in server/db/).
 */
fun main() {
    val projectId = System.getenv("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() }
        ?: error(
            "FIREBASE_PROJECT_ID is not set. Put firebase.projectId=<your project id> in local.properties " +
                "or set the environment variable (see server/README.md).",
        )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val database = AppDatabase(System.getenv("DB_URL") ?: AppDatabase.DEFAULT_URL)
    val firebase = FirebaseAuthConfig.forProject(projectId)
    // 0.0.0.0 = reachable from the emulator (10.0.2.2) and, via `adb reverse`, from a phone.
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(database, firebase) }.start(wait = true)
}

/**
 * Sets up the whole server. Tests call this same function inside `testApplication`
 * (with an in-memory database and their own token keys), so they run the production code.
 */
fun Application.module(database: AppDatabase, firebase: FirebaseAuthConfig) {
    configureSerialization()
    configureValidation()
    configureLogging()
    configureErrors()
    configureAuthentication(firebase)
    configureRouting(database)
}

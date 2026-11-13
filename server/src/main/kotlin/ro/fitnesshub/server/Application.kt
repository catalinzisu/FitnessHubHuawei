package ro.fitnesshub.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ro.fitnesshub.server.db.AppDatabase

/**
 * Entry point of `./gradlew :server:run`. Settings come from environment variables:
 * PORT (default 8080) and DB_URL (default: a file in server/db/).
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val database = AppDatabase(System.getenv("DB_URL") ?: AppDatabase.DEFAULT_URL)
    // 0.0.0.0 = reachable from the emulator (10.0.2.2) and, via `adb reverse`, from a phone.
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(database) }.start(wait = true)
}

/**
 * Sets up the whole server. Tests call this same function inside `testApplication`
 * (with an in-memory database), so they run exactly the code that runs in production.
 */
fun Application.module(database: AppDatabase) {
    configureSerialization()
    configureLogging()
    configureErrors()
    configureRouting(database)
}

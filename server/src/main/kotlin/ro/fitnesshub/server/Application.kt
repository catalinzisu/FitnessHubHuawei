package ro.fitnesshub.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Entry point of `./gradlew :server:run`. The port can be changed with the PORT environment variable. */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // 0.0.0.0 = reachable from the emulator (10.0.2.2) and, via `adb reverse`, from a phone.
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/**
 * Sets up the whole server. Tests call this same function inside `testApplication`,
 * so they run exactly the code that runs in production.
 */
fun Application.module() {
    configureSerialization()
    configureLogging()
    configureErrors()
    configureRouting()
}

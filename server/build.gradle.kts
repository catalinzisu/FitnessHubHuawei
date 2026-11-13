// Fitness Hub backend: a Ktor server that runs on the JVM (not on the phone).
// Start it with:  ./gradlew :server:run   (listens on port 8080, or $PORT)
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // @Serializable request/response classes
    application // adds the "run" task
}

application {
    mainClass.set("ro.fitnesshub.server.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

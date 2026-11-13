import java.util.Properties

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

// `run` passes the Firebase project id from local.properties (firebase.projectId=...) unless the
// FIREBASE_PROJECT_ID environment variable is already set. local.properties is not in git.
tasks.named<JavaExec>("run") {
    val localProperties = rootProject.file("local.properties")
    if (System.getenv("FIREBASE_PROJECT_ID") == null && localProperties.exists()) {
        val properties = Properties().apply { localProperties.inputStream().use { load(it) } }
        properties.getProperty("firebase.projectId")?.let { environment("FIREBASE_PROJECT_ID", it) }
    }
}

dependencies {
    implementation(project(":shared")) // the API's JSON classes, shared with the app
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt) // checks Firebase ID tokens (JWT); brings java-jwt + jwks-rsa
    implementation(libs.exposed.core) // SQL from Kotlin code (tables, queries)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2) // the database itself: a single file, no install needed
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation) // test client sends/reads JSON
    testImplementation(libs.kotlin.test.junit)
}

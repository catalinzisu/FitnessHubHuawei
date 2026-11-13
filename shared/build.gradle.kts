import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The API's JSON classes (ro.fitnesshub.api), used by both the Android app and the Ktor server.
// Plain Kotlin, no Android and no Ktor, so either side can depend on it.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // generates the serializers for the @Serializable classes
}

// The app is built for Java 17, so this module must not use anything newer.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.core)
}

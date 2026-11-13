import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Generates serializers for the @Serializable navigation routes (ui/navigation/Routes.kt).
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase reads its project settings from app/google-services.json, downloaded from the
// Firebase console. The file is not in git, so the plugin only runs when it is there:
// without it (e.g. on CI) the app still builds, and sign-in reports "not configured".
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Machine-specific settings that are not in git (local.properties), e.g. server.url=http://10.0.2.2:8080
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "ro.fitnesshub"
    compileSdk = 37
    // Match the build-tools already installed, so Gradle doesn't try to download 35.0.0.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ro.fitnesshub"
        // Health Connect client 1.1.0 needs API 26+; the S26 runs Android 16.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Address of our Ktor server, read from local.properties instead of being written in the code.
        // Empty when missing (e.g. on CI): the app then works without the server.
        buildConfigField("String", "SERVER_URL", "\"${localProperties.getProperty("server.url", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Kotlin is built into AGP 9; its jvmTarget follows targetCompatibility below.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // REST client for our Ktor server. The JSON classes come from :shared, the same ones the server uses.
    implementation(project(":shared"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.ktor.client.mock) // fake server responses for the API client tests
}

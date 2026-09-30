import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// Imported explicitly: in the Gradle Kotlin DSL a bare `java` resolves to the Java plugin
// extension, which shadows the `java.util` package.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Secrets and signing material are read from local.properties, which is git-ignored.
// An absent key is NOT a build failure — a reviewer must still be able to compile the
// project. It fails loudly at runtime instead (see NetworkModule).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(name: String): String? =
    localProperties.getProperty(name) ?: System.getenv(name)

val tmdbAccessToken: String = localProperty("TMDB_READ_ACCESS_TOKEN").orEmpty()

// Debug builds may point at another server (a local mock, a proxy) via TMDB_BASE_URL. Release
// builds always use the real API — see the buildTypes block.
val tmdbBaseUrlDefault = "https://api.themoviedb.org/3/"
val tmdbBaseUrlOverride: String? = localProperty("TMDB_BASE_URL")
val releaseStoreFile: String? = localProperty("RELEASE_STORE_FILE")

android {
    namespace = "com.application.alfaflix"

    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.application.alfaflix"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Empty when unset: the project must compile without credentials (see NetworkModule).
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"$tmdbAccessToken\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"$tmdbBaseUrlDefault\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = localProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            tmdbBaseUrlOverride?.let { buildConfigField("String", "TMDB_BASE_URL", "\"$it\"") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Fall back to the debug keystore when no release keystore is configured, so
            // `assembleRelease` always produces an installable APK without committing a
            // keystore to version control.
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Required by Robolectric, which runs the Room and Compose tests on the JVM.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room exports its schema here so future versions can be migrated against a known
    // baseline rather than guessed at. Favorites are user-owned data — losing them to a
    // destructive migration would be data loss.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Async
    implementation(libs.kotlinx.coroutines.android)

    // YouTube trailer playback (IFrame Player API in a WebView)
    implementation(libs.youtube.player)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Unit tests (JVM, including Robolectric-backed Room and Compose tests)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

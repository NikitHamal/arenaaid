plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.arena.aid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arena.aid"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        val googleWebClientId = System.getenv("GOOGLE_WEB_CLIENT_ID")
            ?: (project.findProperty("GOOGLE_WEB_CLIENT_ID") as? String)
            ?: "68143624035-que25r0vmrke4agasr715j5u9p8gic2s.apps.googleusercontent.com"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "ARENA_BASE_URL", "\"https://arena.ai\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("en")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/arena-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
                ?: "arenakeystore"
            keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("KEY_ALIAS") as? String)
                ?: "arena"
            keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("KEY_PASSWORD") as? String)
                ?: "arenakeystore"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/com/android/build/gradle/**",
                "/META-INF/proguard/**",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/**/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/**/NOTICE*",
                "/okhttp3/internal/publicsuffix/NOTICE",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
                "/*.proto",
                "/*.properties"
            )
        }
        dex {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Material 3 Expressive
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Activity & Core & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Room Database (Offline Chat, Code Artifacts, Agent Runs, Leaderboard Cache)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Coil Image Loading (memory-capped for 4GB RAM / Android Go)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Baseline Profile Installer for AOT speed on low-end CPUs
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // DataStore & Encrypted SharedPreferences
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.security:security-crypto:1.1.0")

    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")

    // Networking & SSE Streaming
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google Sign-In (Credential Manager + Custom Tabs OAuth)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("androidx.browser:browser:1.10.0")

    testImplementation("junit:junit:4.13.2")
}

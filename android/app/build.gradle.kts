import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        FileInputStream(localFile).use { load(it) }
    }
}

val googleWebClientId: String = providers.environmentVariable("GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.gradleProperty("GOOGLE_WEB_CLIENT_ID"))
    .getOrElse(localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", ""))
    .trim().replace("\"", "").replace("'", "")

val apiBaseUrl: String = providers.environmentVariable("API_BASE_URL")
    .orElse(providers.gradleProperty("API_BASE_URL"))
    .getOrElse(localProperties.getProperty("API_BASE_URL", ""))
    .trim().replace("\"", "").replace("'", "")

val apiSecretKey: String = providers.environmentVariable("API_SECRET_KEY")
    .orElse(providers.gradleProperty("API_SECRET_KEY"))
    .getOrElse(localProperties.getProperty("API_SECRET_KEY", "upi_easy_sec_shared_auth_key_2026_9f8b2c4e"))
    .trim().replace("\"", "").replace("'", "")

// Build-time XOR obfuscation (Mask 0x5A) to prevent reverse-engineering of the secret from DEX bytecode
val xorMask: Byte = 0x5A
val rawKeyBytes = apiSecretKey.toByteArray(Charsets.UTF_8)
val obfuscatedBytes = rawKeyBytes.map { (it.toInt() xor xorMask.toInt()).toByte() }
val obfuscatedBytesLiteral = "new byte[]{" + obfuscatedBytes.joinToString(",") { "(byte)" + it.toInt() } + "}"

android {
    namespace = "com.aerotech.upieasy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aerotech.upieasy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("byte[]", "OBFUSCATED_SECRET_KEY", obfuscatedBytesLiteral)
        resValue("string", "default_web_client_id", googleWebClientId)
        resValue("string", "api_base_url", apiBaseUrl)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isShrinkResources = false
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Prevents build warnings for native libraries that can't be stripped
            // (ML Kit barhopper, DataStore shared counter, image processing JNI)
            useLegacyPackaging = true
            keepDebugSymbols += listOf(
                "**/libbarhopper_v3.so",
                "**/libdatastore_shared_counter.so",
                "**/libimage_processing_util_jni.so"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose UI & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking (Retrofit + OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:24.0.0")

    // Biometrics & Keystore
    implementation("androidx.biometric:biometric:1.1.0")

    // Google Credential Manager & Google ID Token
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // CameraX & ML Kit Barcode Scanning
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")

    // Interactive Step-by-Step Onboarding Tourtip
    implementation("com.fappslab.tourtip:tourtip:1.06.1")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
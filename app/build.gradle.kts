// VedInsta - app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
    alias(libs.plugins.ksp)
}

// Version constants - single source of truth
val appVersionCode = 2
val appVersionName = "1.0.1"
val appName        = "VedInsta"

android {
    namespace  = "com.devson.vedinsta"
    compileSdk = 36

    defaultConfig {
        applicationId          = "com.devson.vedinsta"
        minSdk                 = 26
        targetSdk              = 36
        versionCode            = appVersionCode
        versionName            = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ABI Splits - separate APKs per architecture + one universal fallback
    flavorDimensions += "abi"

    productFlavors {
        // arm64-v8a  - modern 64-bit ARM (most Android 8+ phones)
        create("arm64") {
            dimension       = "abi"
            versionCode     = appVersionCode + 2   // highest store priority
            ndk { abiFilters += "arm64-v8a" }
        }

        // x86_64 - emulators + Chrome OS
        create("x86_64") {
            dimension       = "abi"
            versionCode     = appVersionCode + 1
            ndk { abiFilters += "x86_64" }
        }

        // Universal - all supported ABIs in one APK (GitHub Releases / sideload)
        create("universal") {
            dimension       = "abi"
            versionCode     = appVersionCode        // lowest store priority
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }

    // Signing
    signingConfigs {
        // Release signing - configure via environment variables or local.properties
        // so the keystore path is never committed to source control.
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile     = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias      = System.getenv("KEY_ALIAS")         ?: ""
                keyPassword   = System.getenv("KEY_PASSWORD")      ?: ""
            }
        }
    }

    // Build Types
    buildTypes {
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only apply release signing when keystore env vars are present
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            isMinifyEnabled    = false
            isDebuggable       = true
            applicationIdSuffix = ".debug"
            versionNameSuffix  = "-debug"
        }
    }

    // APK output naming - VedInsta-v1.0.0-arm64-v8a-release.apk
    applicationVariants.all {
        val variant      = this
        val vName        = variant.versionName          // e.g. "1.0.0"
        val flavorName   = variant.flavorName           // e.g. "arm64"
        val buildTypeName = variant.buildType.name      // "release" or "debug"

        // Map flavor → ABI display name used in filename
        val abiLabel = when (flavorName) {
            "arm64"    -> "arm64-v8a"
            "x86_64"   -> "x86_64"
            "universal"-> "universal"
            else       -> flavorName
        }

        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName =
                "$appName-v$vName-$abiLabel-$buildTypeName.apk"
        }
    }

    // Compile options
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    // Packaging - strip unused licence/metadata files to reduce APK size
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "**/kotlin/**",
                "**/*.kotlin_metadata",
                "**/*.version",
                "**/kotlin-tooling-metadata.json"
            )
        }
        jniLibs.useLegacyPackaging = true
    }

    ndkVersion = "27.0.12077973"
}

// Chaquopy (Python runtime)
chaquopy {
    defaultConfig {
        version = "3.13"

        // Use local Python if available; CI/CD can set PYTHON_PATH env var.
        val pythonPath = System.getenv("PYTHON_PATH")
            ?: "C:\\Users\\DEVENDRA\\AppData\\Local\\Programs\\Python\\Python313\\python.exe"
        buildPython(pythonPath)

        // Keep source bytecode at minimum - reduces APK size
        pyc {
            src    = false
            pip    = false
            stdlib = false
        }

        pip {
            install("requests")
            install("pillow")
            install("beautifulsoup4")
        }
    }
}

// Dependencies
dependencies {
    // --- AndroidX Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)

    // --- Lifecycle ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Jetpack Compose (BOM aligns all Compose versions) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Room Database ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- WorkManager ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Security (EncryptedSharedPreferences) ---
    implementation(libs.androidx.security.crypto)

    // --- App Startup ---
    implementation(libs.androidx.startup.runtime)

    // --- Material Components (provides XML Theme.Material3.* for Activity window setup) ---
    implementation(libs.material.components)

    // --- Coil (image + video thumbnails + Compose) ---
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)

    // --- Networking ---
    implementation(libs.okhttp)
    implementation(libs.gson)
    debugImplementation(libs.okhttp.logging)   // only in debug builds

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
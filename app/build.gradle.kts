plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
    kotlin("kapt")
}

android {
    namespace = "com.devson.vedinsta"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devson.vedinsta"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "abi"

    productFlavors {
        create("arm64") {
            dimension = "abi"
            versionCode = 3  // Highest priority
            versionNameSuffix = "-arm64"
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }

        create("arm32") {
            dimension = "abi"
            versionCode = 2
            versionNameSuffix = "-arm32"
            ndk {
                abiFilters.clear()
                abiFilters += "armeabi-v7a"
            }
        }

        create("x86") {
            dimension = "abi"
            versionCode = 4
            versionNameSuffix = "-x86"
            ndk {
                abiFilters.clear()
                abiFilters += "x86_64"
            }
        }

        create("universal") {
            dimension = "abi"
            versionCode = 1
            versionNameSuffix = "-universal"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }
    signingConfigs {
        create("release") {
            // This is a placeholder for your signing configuration.
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        resValues = false
    }

    ndkVersion = "27.0.12077973"
}

chaquopy {
    defaultConfig {
        version = "3.11"
        buildPython("C:\\Users\\DEVENDRA\\AppData\\Local\\Programs\\Python\\Python311\\python.exe")

        pyc {
            src = false
            pip = false
            stdlib = false
        }

        pip {
            install("instaloader")
            install("requests")
            install("pillow")
            install("beautifulsoup4")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Add SwipeRefreshLayout dependency
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    // WorkManager for background downloads
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.google.code.gson:gson:2.11.0")


    // Notification and Startup
    implementation("androidx.startup:startup-runtime:1.2.0")

    // Download and HTTP
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
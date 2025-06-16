plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "fun.fifu.stellareyes"
    compileSdk = 36

    buildFeatures {
        compose = true
    }

//    composeOptions {
//        kotlinCompilerExtensionVersion = "1.5.4"
//    }

    defaultConfig {
        applicationId = "fun.fifu.stellareyes"
        minSdk = 29
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
//            useLegacyPackaging = true
        }
    }
}

dependencies {    // ... other dependencies
    implementation(libs.google.accompanist.permissions)
// Check for the latest version
    implementation(libs.androidx.ui) // Or the latest version
    implementation(libs.androidx.material3) // Or the latest version for Material Design 3
    implementation(libs.androidx.ui.tooling.preview) // Or the latest version
    androidTestImplementation(libs.androidx.ui.test.junit4) // Or the latest version
    debugImplementation(libs.androidx.ui.tooling) // Or the latest version
    debugImplementation(libs.androidx.ui.test.manifest) // Or the latest version
    implementation(libs.androidx.material.icons.core) // Or the latest version
    implementation(libs.androidx.material.icons.extended) // For more icons
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // TensorFlow Lite dependencies
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.support)

    // For ViewModel and LiveData integration (optional but common)
    implementation(libs.androidx.lifecycle.viewmodel.compose) // Or the latest version
    implementation(libs.androidx.runtime.livedata) // Or the latest version
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.capturable)
    // For Navigation (optional)
    implementation(libs.androidx.navigation.compose) // Or the latest version

    implementation(libs.face.detection)
    implementation(libs.play.services.mlkit.face.detection)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.litert)
    implementation(libs.guava)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
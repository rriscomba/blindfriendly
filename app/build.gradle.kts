plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.blindfriend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blindfriend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")  // only modern ARM64 phones
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES"
        )
        // Don't compress ONNX models — Sherpa needs to mmap them
        jniLibs.useLegacyPackaging = false
    }

    androidResources {
        noCompress += listOf("onnx", "task")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // MediaPipe Tasks GenAI (Gemma) + Vision (MPImage / BitmapImageBuilder)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("com.google.mediapipe:tasks-vision:0.10.27")

    // Sherpa-ONNX (Piper TTS engine) — via JitPack, pre-built AAR from GitHub releases
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.0")
}

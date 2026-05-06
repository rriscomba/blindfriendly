# Blind-Friendly v3 — Specification for Implementation

> Voice-first Android assistant for blind users. Runs Gemma 4 E2B + Piper TTS fully on-device, no internet required after model download. Inspired by [marttp/Blind-Friendly-v2](https://github.com/marttp/Blind-Friendly-v2), redesigned for sub-3-second latency to first spoken word.

This document is the complete specification. An implementer should be able to build the project end-to-end from this file without further input.

---

## 1. Goals

- User opens app → hears "¿En qué te ayudo?" within 1 second
- User speaks request in Spanish → hears confirmation in < 1 second
- User hears first word of analysis in < 3 seconds (streaming)
- Continuous navigation mode for walking guidance
- 100% offline after initial model download
- Designed for blind users: voice-only flow, no required screen interaction

## 2. Non-Goals (v1)

- iOS support
- English / multilingual UI (Spanish-only for now, expandable later)
- Cloud fallback
- Voice cloning / custom voices
- Map integration / GPS navigation
- Multi-user profiles

---

## 3. Compatibility Matrix

Use exactly these versions. They have been verified to work together.

| Component | Version | Source |
|---|---|---|
| Android minSdk | 26 | MediaPipe GenAI requires |
| Android targetSdk | 35 | |
| Kotlin | 2.0.21 | |
| AGP (Android Gradle Plugin) | 8.7.0 | |
| Gradle | 8.10 | |
| JDK | 17 | |
| Compose BOM | 2024.12.01 | |
| MediaPipe Tasks GenAI | 0.10.27 | Maven Central |
| Sherpa-ONNX Android | 1.11.5 | Maven Central |
| CameraX | 1.4.1 | |
| Coroutines | 1.10.1 | |
| Lifecycle | 2.8.7 | |

### Hardware requirements

- Android 8.0+ (API 26)
- 8 GB RAM minimum, 12 GB recommended
- ~3 GB free storage for models
- GPU with OpenCL/Vulkan (auto-fallback to CPU)

---

## 4. Architecture

```
┌────────────────────────────────────────────────────────┐
│ MainActivity (single-screen, Compose)                  │
└──────────────────────┬─────────────────────────────────┘
                       │ binds
                       ▼
┌────────────────────────────────────────────────────────┐
│ AssistantService (foreground)                          │
└──────────────────────┬─────────────────────────────────┘
                       │ owns
                       ▼
┌────────────────────────────────────────────────────────┐
│ AssistantViewModel — state machine + orchestration     │
│                                                        │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│   │  Speech  │  │  Camera  │  │  Piper   │             │
│   │ Listener │  │Controller│  │   TTS    │             │
│   └────┬─────┘  └────┬─────┘  └────▲─────┘             │
│        │             │             │                   │
│        ▼             ▼             │                    │
│   text request  bitmap frame  audio stream             │
│        │             │             ▲                    │
│        ▼             ▼             │                    │
│   ┌────────────────────────────────┴───┐                │
│   │ GemmaInference (mutex-guarded)     │                │
│   │   ├── textSession (intent classify)│                │
│   │   └── visionSession (multimodal)   │                │
│   └────────────────────────────────────┘                │
└────────────────────────────────────────────────────────┘
```

**Critical design decisions:**

1. **Eager initialization in Application class.** Gemma model load takes 5-10s. Start loading the moment the process boots, in parallel with Piper. The greeting "¿En qué te ayudo?" hides this latency.

2. **Two persistent Gemma sessions.** One text-only for intent classification (fast, sub-second), one multimodal for image analysis. Recreating sessions per request is too slow.

3. **Mutex on Gemma.** MediaPipe `LlmInference` does NOT support concurrent inference. Wrap every call in a mutex.

4. **Latest-wins frame buffer.** Camera produces 30fps; Gemma can only process every few seconds. Keep only the most recent frame, drop the rest. Never queue.

5. **Sentence-chunked TTS streaming.** As Gemma streams tokens, accumulate until a sentence boundary (`. , ! ?`), then send to Piper. User hears first sentence within 2-3 seconds while remainder is still generating.

6. **Foreground service mandatory.** Without it, Android kills the app when the screen is off. Critical for blind users who don't look at the device.

---

## 5. Project Structure

```
blind-friendly-v3/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
├── SPEC.md                          ← this file
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── piper/
│       │       ├── es_ES-sharvard-medium.onnx       (download separately, see §11)
│       │       ├── es_ES-sharvard-medium.onnx.json
│       │       ├── tokens.txt
│       │       └── espeak-ng-data/                  (extracted)
│       ├── res/
│       │   ├── drawable/ic_assist.xml
│       │   ├── values/strings.xml
│       │   ├── values/themes.xml
│       │   └── xml/
│       │       └── network_security_config.xml
│       └── java/com/blindfriend/
│           ├── BlindFriendApp.kt
│           ├── MainActivity.kt
│           ├── service/
│           │   └── AssistantService.kt
│           ├── viewmodel/
│           │   └── AssistantViewModel.kt
│           ├── llm/
│           │   ├── GemmaInference.kt
│           │   └── LlmConstants.kt
│           ├── tts/
│           │   ├── PiperTtsEngine.kt
│           │   └── SentenceChunker.kt
│           ├── stt/
│           │   └── SpeechListener.kt
│           ├── camera/
│           │   ├── CameraController.kt
│           │   ├── FrameChangeDetector.kt
│           │   └── BitmapExt.kt
│           ├── intent/
│           │   ├── AssistMode.kt
│           │   ├── UserIntent.kt
│           │   ├── IntentClassifier.kt
│           │   └── PromptBuilder.kt
│           └── ui/
│               └── HomeScreen.kt
```

---

## 6. Gradle Configuration

### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BlindFriend"
include(":app")
```

### `build.gradle.kts` (root)
```kotlin
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

### `app/build.gradle.kts`
```kotlin
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

    // MediaPipe Tasks GenAI (Gemma)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // Sherpa-ONNX (Piper TTS engine)
    implementation("com.k2-fsa:sherpa-onnx-android:1.11.5")
}
```

### `gradle.properties`
```
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 7. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.microphone" android:required="true" />

    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
    </queries>

    <application
        android:name=".BlindFriendApp"
        android:allowBackup="false"
        android:icon="@android:drawable/ic_btn_speak_now"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:largeHeap="true"
        android:theme="@style/Theme.BlindFriend">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.AssistantService"
            android:exported="false"
            android:foregroundServiceType="camera|microphone" />

    </application>
</manifest>
```

---

## 8. Source Files

### 8.1 `BlindFriendApp.kt`

```kotlin
package com.blindfriend

import android.app.Application
import com.blindfriend.llm.GemmaInference
import com.blindfriend.tts.PiperTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlindFriendApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var gemma: GemmaInference
        private set
    lateinit var piper: PiperTtsEngine
        private set

    val initJob by lazy {
        appScope.launch {
            val gemmaJob = launch(Dispatchers.IO) {
                gemma = GemmaInference(this@BlindFriendApp).apply { init() }
            }
            val piperJob = launch(Dispatchers.IO) {
                piper = PiperTtsEngine(this@BlindFriendApp).apply { init() }
            }
            gemmaJob.join()
            piperJob.join()
        }
    }

    override fun onCreate() {
        super.onCreate()
        initJob // trigger lazy
    }
}
```

### 8.2 `MainActivity.kt`

```kotlin
package com.blindfriend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.blindfriend.service.AssistantService
import com.blindfriend.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() {
            val base = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                base + Manifest.permission.POST_NOTIFICATIONS
            } else base
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            AssistantService.start(this)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }

        if (requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            AssistantService.start(this)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }
}
```

### 8.3 `service/AssistantService.kt`

```kotlin
package com.blindfriend.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.blindfriend.MainActivity
import com.blindfriend.R

class AssistantService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Asistente activo")
            .setSmallIcon(R.drawable.ic_assist)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Asistente", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "assistant_channel"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
```

### 8.4 `llm/LlmConstants.kt`

```kotlin
package com.blindfriend.llm

object LlmConstants {
    // Path where the model is pushed via adb (see §11)
    const val MODEL_PATH = "/data/local/tmp/llm/gemma4-e2b.task"

    // Output budget — navigation prompts are short
    const val MAX_TOKENS = 256

    // Sampling — low temperature for predictable, fast verification
    const val TEMPERATURE = 0.3f
    const val TOP_K = 20
    const val TOP_P = 0.9f

    // Image input
    const val MAX_IMAGE_DIM = 512
}
```

### 8.5 `llm/GemmaInference.kt`

```kotlin
package com.blindfriend.llm

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GemmaInference(private val context: Context) {

    private lateinit var llm: LlmInference

    /** Text-only session for intent classification (fast). */
    private lateinit var textSession: LlmInferenceSession

    /** Multimodal session for image analysis. */
    private lateinit var visionSession: LlmInferenceSession

    fun init() {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(LlmConstants.MODEL_PATH)
            .setMaxTokens(LlmConstants.MAX_TOKENS)
            .setMaxNumImages(1)
            .setPreferredBackend(Backend.GPU) // falls back to CPU if unavailable
            .build()

        llm = LlmInference.createFromOptions(context, options)

        val baseSessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(LlmConstants.TOP_K)
            .setTopP(LlmConstants.TOP_P)
            .setTemperature(LlmConstants.TEMPERATURE)

        textSession = LlmInferenceSession.createFromOptions(
            llm, baseSessionOpts.build()
        )

        visionSession = LlmInferenceSession.createFromOptions(
            llm,
            baseSessionOpts
                .setGraphOptions(
                    GraphOptions.builder().setEnableVisionModality(true).build()
                )
                .build()
        )
    }

    /**
     * Text-only generation. Used for intent classification.
     * Caller must serialize calls externally (Mutex).
     */
    suspend fun generateText(prompt: String): String = suspendCancellableCoroutine { cont ->
        val sb = StringBuilder()
        textSession.addQueryChunk(prompt)
        textSession.generateResponseAsync { partial, done ->
            sb.append(partial)
            if (done && cont.isActive) {
                cont.resume(sb.toString())
                // Reset session state for next query
                resetTextSession()
            }
        }
    }

    /**
     * Multimodal generation with streaming callback.
     * Tokens are delivered incrementally; `done=true` signals completion.
     */
    fun generateMultimodal(
        prompt: String,
        bitmap: Bitmap,
        onToken: (String, Boolean) -> Unit
    ) {
        visionSession.addQueryChunk(prompt)
        visionSession.addImage(BitmapImageBuilder(bitmap).build())
        visionSession.generateResponseAsync { partial, done ->
            onToken(partial, done)
            if (done) resetVisionSession()
        }
    }

    private fun resetTextSession() {
        textSession.close()
        textSession = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(LlmConstants.TOP_K)
                .setTopP(LlmConstants.TOP_P)
                .setTemperature(LlmConstants.TEMPERATURE)
                .build()
        )
    }

    private fun resetVisionSession() {
        visionSession.close()
        visionSession = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(LlmConstants.TOP_K)
                .setTopP(LlmConstants.TOP_P)
                .setTemperature(LlmConstants.TEMPERATURE)
                .setGraphOptions(
                    GraphOptions.builder().setEnableVisionModality(true).build()
                )
                .build()
        )
    }

    fun close() {
        textSession.close()
        visionSession.close()
        llm.close()
    }
}
```

### 8.6 `tts/PiperTtsEngine.kt`

```kotlin
package com.blindfriend.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File

class PiperTtsEngine(private val context: Context) {

    private lateinit var tts: OfflineTts
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackQueue = Channel<String>(Channel.UNLIMITED)
    private val chunker = SentenceChunker()

    private var currentTrack: AudioTrack? = null

    fun init() {
        val baseDir = copyAssetsIfNeeded()

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$baseDir/es_ES-sharvard-medium.onnx",
                    tokens = "$baseDir/tokens.txt",
                    dataDir = "$baseDir/espeak-ng-data",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        tts = OfflineTts(config = config)

        // Start playback worker
        scope.launch { playbackWorker() }
    }

    private fun copyAssetsIfNeeded(): String {
        val targetDir = File(context.filesDir, "piper")
        val marker = File(targetDir, ".ready")
        if (marker.exists()) return targetDir.absolutePath

        targetDir.mkdirs()
        copyAssetTree("piper", targetDir)
        marker.createNewFile()
        return targetDir.absolutePath
    }

    private fun copyAssetTree(assetPath: String, dest: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // It's a file
            assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) {
            copyAssetTree("$assetPath/$child", File(dest, child))
        }
    }

    /** Speak a complete utterance. Suspends until audio finishes. */
    suspend fun speakBlocking(text: String) {
        val audio = tts.generate(text = text, sid = 0, speed = 1.0f)
        playPcm(audio.samples, audio.sampleRate)
    }

    /** Speak a complete utterance asynchronously (queued). */
    fun speak(text: String) {
        playbackQueue.trySend(text)
    }

    /**
     * Streaming token input — accumulates until a sentence boundary,
     * then enqueues the sentence for synthesis.
     */
    fun streamToken(token: String) {
        val sentence = chunker.feed(token)
        if (sentence != null) playbackQueue.trySend(sentence)
    }

    /** Flush remaining buffered text after generation completes. */
    fun flushStream() {
        val remainder = chunker.flush()
        if (remainder.isNotBlank()) playbackQueue.trySend(remainder)
    }

    /** Stop current playback and clear queue. */
    fun stop() {
        currentTrack?.runCatching { pause(); flush() }
        chunker.reset()
        // Drain queue
        while (playbackQueue.tryReceive().isSuccess) { /* drain */ }
    }

    private suspend fun playbackWorker() {
        for (text in playbackQueue) {
            try {
                val audio = tts.generate(text = text, sid = 0, speed = 1.0f)
                playPcm(audio.samples, audio.sampleRate)
            } catch (_: Exception) { /* swallow & continue */ }
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = track
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        // Wait for playback to finish
        val durationMs = (samples.size.toLong() * 1000L) / sampleRate
        Thread.sleep(durationMs + 50)
        track.release()
        currentTrack = null
    }

    fun release() {
        scope.cancel()
        tts.release()
        currentTrack?.release()
    }
}
```

### 8.7 `tts/SentenceChunker.kt`

```kotlin
package com.blindfriend.tts

/**
 * Accumulates streamed tokens and emits complete sentences.
 * Triggers on `. , ! ? ;` followed by space or end.
 */
class SentenceChunker {

    private val buf = StringBuilder()
    private val terminators = setOf('.', ',', '!', '?', ';')
    private val minChunkLen = 8 // avoid splitting on initial commas in greetings

    /** Returns a sentence ready to speak, or null if more input needed. */
    fun feed(token: String): String? {
        buf.append(token)
        return findChunk()
    }

    fun flush(): String {
        val out = buf.toString().trim()
        buf.clear()
        return out
    }

    fun reset() {
        buf.clear()
    }

    private fun findChunk(): String? {
        val s = buf.toString()
        if (s.length < minChunkLen) return null
        for (i in minChunkLen until s.length) {
            if (s[i] in terminators) {
                val chunk = s.substring(0, i + 1).trim()
                buf.clear()
                if (i + 1 < s.length) buf.append(s.substring(i + 1))
                if (chunk.isNotBlank()) return chunk
            }
        }
        return null
    }
}
```

### 8.8 `stt/SpeechListener.kt`

```kotlin
package com.blindfriend.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechListener(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun listen(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconocimiento de voz no disponible")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p0: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No te escuché"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                        SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                        else -> "Error $error"
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (text != null) onResult(text) else onError("Sin resultados")
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
```

### 8.9 `camera/CameraController.kt`

```kotlin
package com.blindfriend.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    fun start(onFrame: (Bitmap) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(android.util.Size(640, 480))
                .build()

            analysis.setAnalyzer(analyzerExecutor) { proxy ->
                try {
                    val bitmap = proxy.toBitmap()
                    val rotated = bitmap.rotate(proxy.imageInfo.rotationDegrees)
                    onFrame(rotated)
                } finally {
                    proxy.close()
                }
            }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        analyzerExecutor.shutdown()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
```

### 8.10 `camera/FrameChangeDetector.kt`

```kotlin
package com.blindfriend.camera

import android.graphics.Bitmap
import kotlin.math.abs

class FrameChangeDetector(
    private val changeThreshold: Double = 0.08,
    private val cooldownMs: Long = 3000L
) {
    private var lastBitmap: Bitmap? = null
    private var lastAnalysisTime: Long = 0

    fun shouldAnalyze(frame: Bitmap): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < cooldownMs) return false

        val last = lastBitmap
        if (last == null) {
            update(frame, now)
            return true
        }
        if (computeChange(frame, last) > changeThreshold) {
            update(frame, now)
            return true
        }
        return false
    }

    private fun update(frame: Bitmap, time: Long) {
        lastBitmap?.takeIf { !it.isRecycled }?.recycle()
        lastBitmap = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)
        lastAnalysisTime = time
    }

    private fun computeChange(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        val step = 8
        var diff = 0L
        var count = 0
        for (x in 0 until a.width step step) {
            for (y in 0 until a.height step step) {
                val pa = a.getPixel(x, y); val pb = b.getPixel(x, y)
                diff += abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)).toLong()
                diff += abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)).toLong()
                diff += abs((pa and 0xFF) - (pb and 0xFF)).toLong()
                count++
            }
        }
        return diff.toDouble() / (count * 3 * 255)
    }
}
```

### 8.11 `camera/BitmapExt.kt`

```kotlin
package com.blindfriend.camera

import android.graphics.Bitmap
import com.blindfriend.llm.LlmConstants

fun Bitmap.prepareForLlm(): Bitmap {
    val max = LlmConstants.MAX_IMAGE_DIM
    val scale = minOf(max.toFloat() / width, max.toFloat() / height)
    if (scale >= 1f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt(),
        (height * scale).toInt(),
        true
    )
}
```

### 8.12 `intent/AssistMode.kt`

```kotlin
package com.blindfriend.intent

enum class AssistMode {
    NAVIGATION, FIND_OBJECT, LIGHT_CHECK, PERSON_DETECT, READ_TEXT, GENERAL
}
```

### 8.13 `intent/UserIntent.kt`

```kotlin
package com.blindfriend.intent

data class UserIntent(
    val mode: AssistMode,
    val targetObject: String? = null,
    val rawRequest: String = ""
)
```

### 8.14 `intent/IntentClassifier.kt`

```kotlin
package com.blindfriend.intent

import com.blindfriend.llm.GemmaInference

class IntentClassifier(private val gemma: GemmaInference) {

    private val systemPrompt = """
You are an intent classifier. Given a user request in Spanish or English,
respond ONLY with a JSON object. No prose, no explanation.

Schema:
{"mode": "NAVIGATION|FIND_OBJECT|LIGHT_CHECK|PERSON_DETECT|READ_TEXT|GENERAL", "object": "<noun or null>"}

Examples:
"ayúdame a caminar" -> {"mode":"NAVIGATION","object":null}
"encuentra mi reloj" -> {"mode":"FIND_OBJECT","object":"reloj"}
"está la luz prendida" -> {"mode":"LIGHT_CHECK","object":null}
"hay alguien en la habitación" -> {"mode":"PERSON_DETECT","object":null}
"qué dice ese letrero" -> {"mode":"READ_TEXT","object":null}
"describe lo que ves" -> {"mode":"GENERAL","object":null}
""".trimIndent()

    suspend fun classify(userSpeech: String): UserIntent {
        val prompt = "$systemPrompt\n\nUser: \"$userSpeech\"\nJSON:"
        return try {
            val response = gemma.generateText(prompt)
            parseJson(response, userSpeech)
        } catch (_: Exception) {
            UserIntent(AssistMode.GENERAL, rawRequest = userSpeech)
        }
    }

    private fun parseJson(response: String, raw: String): UserIntent {
        val jsonRegex = """\{[^}]+\}""".toRegex()
        val json = jsonRegex.find(response)?.value
            ?: return UserIntent(AssistMode.GENERAL, rawRequest = raw)

        val mode = when {
            "NAVIGATION" in json -> AssistMode.NAVIGATION
            "FIND_OBJECT" in json -> AssistMode.FIND_OBJECT
            "LIGHT_CHECK" in json -> AssistMode.LIGHT_CHECK
            "PERSON_DETECT" in json -> AssistMode.PERSON_DETECT
            "READ_TEXT" in json -> AssistMode.READ_TEXT
            else -> AssistMode.GENERAL
        }
        val target = """"object"\s*:\s*"([^"]+)"""".toRegex()
            .find(json)?.groupValues?.get(1)?.takeIf { it != "null" }

        return UserIntent(mode, target, raw)
    }
}
```

### 8.15 `intent/PromptBuilder.kt`

```kotlin
package com.blindfriend.intent

object PromptBuilder {

    fun build(intent: UserIntent): String = when (intent.mode) {

        AssistMode.NAVIGATION ->
            "Eres guía de una persona ciega caminando. Analiza esta imagen. " +
            "En máximo 2 oraciones cortas: describe el camino y obstáculos inmediatos. " +
            "Sé específico con distancias: 'a 1 metro', 'a tu izquierda'. Sin saludos."

        AssistMode.FIND_OBJECT -> {
            val obj = intent.targetObject ?: "el objeto"
            "Ayudas a una persona ciega a encontrar $obj. Mira la imagen completa. " +
            "Si lo ves: di exactamente dónde está respecto a la cámara (izquierda/derecha/centro, cerca/lejos). " +
            "Si no está visible: di 'no veo $obj' y sugiere mirar otra dirección. Sé breve."
        }

        AssistMode.LIGHT_CHECK ->
            "Analiza la iluminación de esta imagen. En una oración: ¿está la luz prendida o apagada? " +
            "¿Es brillante, tenue u oscuro? Sé directo."

        AssistMode.PERSON_DETECT ->
            "Mira esta imagen. ¿Cuántas personas son visibles? ¿Dónde están (izquierda/derecha/centro, cerca/lejos)? " +
            "Si no hay personas: di 'no veo a nadie'. Una oración por persona máximo."

        AssistMode.READ_TEXT ->
            "Lee todo el texto visible en esta imagen. Transcríbelo exactamente como aparece, en orden de lectura. " +
            "Si no hay texto: di 'no veo texto legible'."

        AssistMode.GENERAL ->
            "Describe lo que ves en máximo 3 oraciones. Enfócate en detalles útiles para una persona ciega: " +
            "objetos, personas, distribución espacial. Sé directo y específico."
    }

    fun confirmationMessage(intent: UserIntent): String = when (intent.mode) {
        AssistMode.NAVIGATION -> "Analizando el camino"
        AssistMode.FIND_OBJECT -> "Buscando ${intent.targetObject ?: "el objeto"}"
        AssistMode.LIGHT_CHECK -> "Revisando la luz"
        AssistMode.PERSON_DETECT -> "Escaneando la habitación"
        AssistMode.READ_TEXT -> "Leyendo el texto"
        AssistMode.GENERAL -> "Analizando"
    }

    fun isContinuous(mode: AssistMode) = mode == AssistMode.NAVIGATION
}
```

### 8.16 `viewmodel/AssistantViewModel.kt`

```kotlin
package com.blindfriend.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.blindfriend.BlindFriendApp
import com.blindfriend.camera.CameraController
import com.blindfriend.camera.FrameChangeDetector
import com.blindfriend.camera.prepareForLlm
import com.blindfriend.intent.IntentClassifier
import com.blindfriend.intent.PromptBuilder
import com.blindfriend.intent.UserIntent
import com.blindfriend.stt.SpeechListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AssistantState {
    object Booting : AssistantState()
    object Greeting : AssistantState()
    object Listening : AssistantState()
    object Classifying : AssistantState()
    data class Analyzing(val intent: UserIntent) : AssistantState()
    data class Continuous(val intent: UserIntent) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as BlindFriendApp
    private val gemma get() = appCtx.gemma
    private val piper get() = appCtx.piper
    private val classifier by lazy { IntentClassifier(gemma) }
    private val frameDetector = FrameChangeDetector()
    private val speechListener = SpeechListener(app)
    private val gemmaMutex = Mutex()

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Booting)
    val state: StateFlow<AssistantState> = _state

    private var camera: CameraController? = null
    private var continuousJob: Job? = null
    private var pendingFrame: Bitmap? = null

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        camera = CameraController(getApplication(), lifecycleOwner).also { c ->
            c.start { bitmap -> onCameraFrame(bitmap) }
        }
    }

    fun onAppReady() {
        viewModelScope.launch {
            appCtx.initJob.join()
            greet()
        }
    }

    private suspend fun greet() {
        _state.value = AssistantState.Greeting
        piper.speakBlocking("¿En qué te ayudo?")
        startListening()
    }

    private fun startListening() {
        _state.value = AssistantState.Listening
        speechListener.listen(
            onResult = { onUserSpeech(it) },
            onError = {
                viewModelScope.launch {
                    piper.speakBlocking("No te escuché, intenta de nuevo")
                    startListening()
                }
            }
        )
    }

    private fun onUserSpeech(text: String) {
        if (text.matches(Regex("(?i).*(parar|detente|cancela|stop).*"))) {
            stopContinuous()
            return
        }
        viewModelScope.launch {
            _state.value = AssistantState.Classifying
            val intent = gemmaMutex.withLock { classifier.classify(text) }
            piper.speak(PromptBuilder.confirmationMessage(intent))
            if (PromptBuilder.isContinuous(intent.mode)) startContinuous(intent)
            else analyzeOnce(intent)
        }
    }

    private suspend fun analyzeOnce(intent: UserIntent) {
        _state.value = AssistantState.Analyzing(intent)
        val frame = waitForFrame() ?: run {
            piper.speakBlocking("No pude acceder a la cámara")
            startListening(); return
        }
        gemmaMutex.withLock {
            val prompt = PromptBuilder.build(intent)
            gemma.generateMultimodal(prompt, frame.prepareForLlm()) { token, done ->
                piper.streamToken(token)
                if (done) {
                    piper.flushStream()
                    viewModelScope.launch {
                        delay(800)
                        piper.speakBlocking("¿Algo más?")
                        startListening()
                    }
                }
            }
        }
    }

    private fun startContinuous(intent: UserIntent) {
        continuousJob?.cancel()
        _state.value = AssistantState.Continuous(intent)
        continuousJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val frame = pendingFrame
                if (frame != null && frameDetector.shouldAnalyze(frame)) {
                    gemmaMutex.withLock {
                        val prompt = PromptBuilder.build(intent)
                        gemma.generateMultimodal(prompt, frame.prepareForLlm()) { t, d ->
                            piper.streamToken(t)
                            if (d) piper.flushStream()
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopContinuous() {
        continuousJob?.cancel()
        continuousJob = null
        piper.stop()
        viewModelScope.launch {
            piper.speakBlocking("¿En qué más te ayudo?")
            startListening()
        }
    }

    private fun onCameraFrame(bitmap: Bitmap) {
        pendingFrame?.takeIf { !it.isRecycled }?.recycle()
        pendingFrame = bitmap
    }

    private suspend fun waitForFrame(timeoutMs: Long = 3000L): Bitmap? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            pendingFrame?.let { return it }
            delay(100)
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        continuousJob?.cancel()
        speechListener.cancel()
        camera?.stop()
        pendingFrame?.takeIf { !it.isRecycled }?.recycle()
    }
}
```

### 8.17 `ui/HomeScreen.kt`

```kotlin
package com.blindfriend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindfriend.viewmodel.AssistantState
import com.blindfriend.viewmodel.AssistantViewModel

@Composable
fun HomeScreen() {
    val vm: AssistantViewModel = viewModel()
    val state by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        vm.bindCamera(lifecycle)
        vm.onAppReady()
    }

    val label = when (state) {
        is AssistantState.Booting -> "Cargando…"
        is AssistantState.Greeting -> "Saludando"
        is AssistantState.Listening -> "Escuchando…"
        is AssistantState.Classifying -> "Pensando"
        is AssistantState.Analyzing -> "Analizando"
        is AssistantState.Continuous -> "Guiando"
        is AssistantState.Error -> "Error"
    }

    Surface(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

---

## 9. Resources

### `res/values/strings.xml`
```xml
<resources>
    <string name="app_name">Asistente</string>
</resources>
```

### `res/values/themes.xml`
```xml
<resources>
    <style name="Theme.BlindFriend" parent="android:Theme.Material.NoActionBar.Fullscreen" />
</resources>
```

### `res/drawable/ic_assist.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M12,2A3,3 0 0,1 15,5V11A3,3 0 0,1 12,14A3,3 0 0,1 9,11V5A3,3 0 0,1 12,2M19,11C19,14.53 16.39,17.44 13,17.93V21H11V17.93C7.61,17.44 5,14.53 5,11H7A5,5 0 0,0 12,16A5,5 0 0,0 17,11H19Z"/>
</vector>
```

---

## 10. Asset Preparation

### Piper Spanish voice

Download once and place in `app/src/main/assets/piper/`:

```bash
cd app/src/main/assets/piper

# Voice model + config
curl -L -o es_ES-sharvard-medium.onnx \
  https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/sharvard/medium/es_ES-sharvard-medium.onnx
curl -L -o es_ES-sharvard-medium.onnx.json \
  https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/sharvard/medium/es_ES-sharvard-medium.onnx.json

# Tokens file (generate using sherpa-onnx helper from the .onnx.json)
# See: https://k2-fsa.github.io/sherpa/onnx/tts/piper.html
# Output: tokens.txt

# espeak-ng phoneme data (shared by all Piper models)
curl -L -o espeak-ng-data.tar.bz2 \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2
tar xf espeak-ng-data.tar.bz2
rm espeak-ng-data.tar.bz2
```

After the first build, the assets will be copied to `filesDir/piper/` on app start.

---

## 11. Gemma 4 Model Setup

The Gemma 4 E2B `.task` file is too large to bundle (~2 GB). It must be pushed to the device manually before first run:

```bash
# Download from Hugging Face (requires accepting Gemma license)
# https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

adb shell rm -rf /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it-litert-lm.task /data/local/tmp/llm/gemma4-e2b.task
```

The path `/data/local/tmp/llm/gemma4-e2b.task` is hardcoded in `LlmConstants.MODEL_PATH`. For production distribution, this should be replaced with a model download flow (out of scope for v1).

---

## 12. Build & Run

```bash
# Install
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Push Gemma model (see §11)
adb push gemma-4-E2B-it-litert-lm.task /data/local/tmp/llm/gemma4-e2b.task

# Launch
adb shell am start -n com.blindfriend/.MainActivity

# Watch logs
adb logcat | grep -E "BlindFriend|MediaPipe|sherpa"
```

---

## 13. Known Gotchas

1. **`addImage()` order matters.** Call `addQueryChunk()` BEFORE `addImage()`, otherwise MediaPipe throws on certain Gemma model versions.

2. **Session reset after each `generateResponseAsync`.** MediaPipe sessions are stateful; without reset the next query inherits the prior context, drifting prompt and exhausting `maxTokens`. The `GemmaInference` class handles this.

3. **Sherpa-ONNX needs `noCompress` for `.onnx`.** Otherwise APK packaging compresses them and Sherpa cannot mmap. The `androidResources { noCompress }` block in `build.gradle.kts` covers this.

4. **`SpeechRecognizer.EXTRA_PREFER_OFFLINE` is best-effort.** Some Android builds still hit Google servers. For fully offline, install Google's offline Spanish voice pack: Settings → System → Languages → Voice input → Google → Offline languages → Spanish.

5. **GPU backend may crash on Mali GPUs older than G77.** If `LlmInference.Backend.GPU` fails, fall back to `Backend.CPU`. Wrap in try/catch in `GemmaInference.init()` if shipping to wider audience.

6. **First-frame race.** The camera takes ~500ms to deliver the first frame; `waitForFrame()` accounts for this with a 3s timeout.

7. **Thermal throttling.** Continuous Gemma inference on GPU heats the phone; after ~10 minutes the SoC throttles and tokens/sec drops by ~50%. Mitigate by cooling pauses in continuous mode (already partially handled by `cooldownMs` in `FrameChangeDetector`).

---

## 14. Testing Checklist

- [ ] Cold start to "¿En qué te ayudo?" < 2 s on Pixel 8 Pro
- [ ] Intent classification returns valid mode for: "encuentra mis llaves", "ayúdame a caminar", "dime si la luz está prendida", "hay alguien aquí", "qué dice esto"
- [ ] First spoken word of analysis arrives < 3 s after image capture
- [ ] Navigation mode produces fresh guidance every 3-5 s when scene changes
- [ ] Navigation mode stops cleanly on user saying "para"
- [ ] Speech recognizer recovers from `ERROR_NO_MATCH` without crashing
- [ ] App survives screen off → screen on cycle (foreground service intact)
- [ ] Memory peak under 2.2 GB on a 12 GB device
- [ ] No crash when GPU init fails (CPU fallback)

---

## 15. Future Work (out of scope for v1)

- Wake word ("Hey amigo") via Porcupine or openWakeWord
- Battery saver mode: lower analysis frequency when device is hot
- Haptic feedback for direction (left/right vibration patterns)
- Wear OS companion: send guidance to a watch
- Settings screen: voice selection, speech rate, language
- Model auto-download with verification (replace adb push)
- Multi-language: English, Portuguese, French (Piper has voices for all)
- Reading mode improvements: OCR with PaddleOCR if Gemma struggles with small text

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

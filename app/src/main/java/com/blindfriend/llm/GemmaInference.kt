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

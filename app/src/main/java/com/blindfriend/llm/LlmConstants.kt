package com.blindfriend.llm

import android.content.Context

object LlmConstants {
    // Model is downloaded to internal storage by ModelDownloader
    private const val MODEL_FILENAME = "gemma-4-E2B-it-litert-lm.task"

    fun modelPath(context: Context): String =
        "${context.filesDir}/llm/$MODEL_FILENAME"

    // Output budget — navigation prompts are short
    const val MAX_TOKENS = 256

    // Sampling — low temperature for predictable, fast verification
    const val TEMPERATURE = 0.3f
    const val TOP_K = 20
    const val TOP_P = 0.9f

    // Image input
    const val MAX_IMAGE_DIM = 512
}

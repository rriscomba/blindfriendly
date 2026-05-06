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

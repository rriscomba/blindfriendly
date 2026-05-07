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

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

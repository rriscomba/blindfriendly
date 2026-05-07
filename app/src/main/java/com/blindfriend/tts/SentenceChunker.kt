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

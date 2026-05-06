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

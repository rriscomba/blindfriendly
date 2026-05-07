package com.blindfriend.download

import android.content.Context
import com.blindfriend.llm.LlmConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {

    companion object {
        private const val HF_REPO = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main"
        private const val MODEL_FILENAME = "gemma-4-E2B-it-litert-lm.task"

        fun modelFile(context: Context): File =
            File(context.filesDir, "llm/$MODEL_FILENAME")

        fun isModelReady(context: Context): Boolean = modelFile(context).let {
            it.exists() && it.length() > 0
        }
    }

    fun download(hfToken: String): Flow<DownloadState> = flow {
        val dest = modelFile(context)
        dest.parentFile?.mkdirs()
        val temp = File(dest.parent, "${dest.name}.tmp")

        val resumeFrom = if (temp.exists()) temp.length() else 0L
        emit(DownloadState.Downloading(0f, resumeFrom, -1L))

        try {
            val conn = (URL("$HF_REPO/$MODEL_FILENAME").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer ${hfToken.trim()}")
                setRequestProperty("User-Agent", "BlindFriend/1.0")
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
                connectTimeout = 15_000
                readTimeout = 60_000
                connect()
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != 206) {
                val msg = when (code) {
                    401 -> "Token inválido o sin acceso a Gemma 4"
                    403 -> "Acceso denegado — aceptá la licencia en huggingface.co"
                    404 -> "Modelo no encontrado en HuggingFace"
                    else -> "Error HTTP $code"
                }
                emit(DownloadState.Error(msg))
                return@flow
            }

            val remoteLen = conn.contentLengthLong
            val totalBytes = if (code == 206 && remoteLen > 0) remoteLen + resumeFrom else remoteLen
            var downloaded = resumeFrom
            val append = code == 206 && resumeFrom > 0

            conn.inputStream.use { input ->
                FileOutputStream(temp, append).use { output ->
                    val buf = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                        emit(DownloadState.Downloading(progress, downloaded, totalBytes))
                    }
                }
            }

            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            emit(DownloadState.Done)

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Error de conexión"))
        }
    }.flowOn(Dispatchers.IO)
}

package com.blindfriend

import android.app.Application
import com.blindfriend.download.ModelDownloader
import com.blindfriend.llm.GemmaInference
import com.blindfriend.tts.PiperTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlindFriendApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var gemma: GemmaInference
        private set
    lateinit var piper: PiperTtsEngine
        private set

    private var _initJob: Job? = null

    /** Suspendable handle to the parallel init of Gemma + Piper. */
    val initJob: Job
        get() = _initJob ?: error("startInit() was not called")

    override fun onCreate() {
        super.onCreate()
        if (ModelDownloader.isModelReady(this)) {
            startInit()
        }
    }

    /** Call once the model file is confirmed present. Idempotent. */
    fun startInit() {
        if (_initJob != null) return
        _initJob = appScope.launch {
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
}

package com.blindfriend

import android.app.Application
import com.blindfriend.llm.GemmaInference
import com.blindfriend.tts.PiperTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlindFriendApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var gemma: GemmaInference
        private set
    lateinit var piper: PiperTtsEngine
        private set

    val initJob by lazy {
        appScope.launch {
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

    override fun onCreate() {
        super.onCreate()
        initJob // trigger lazy
    }
}

package com.blindfriend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.blindfriend.download.ModelDownloader
import com.blindfriend.service.AssistantService
import com.blindfriend.ui.HomeScreen
import com.blindfriend.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() {
            val base = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                base + Manifest.permission.POST_NOTIFICATIONS
            } else base
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            AssistantService.start(this)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var modelReady by remember { mutableStateOf(ModelDownloader.isModelReady(this)) }
            if (modelReady) {
                HomeScreen()
            } else {
                SetupScreen(onReady = { modelReady = true })
            }
        }

        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            AssistantService.start(this)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }
}

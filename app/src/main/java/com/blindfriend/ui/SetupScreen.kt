package com.blindfriend.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindfriend.download.DownloadState
import com.blindfriend.viewmodel.SetupViewModel

@Composable
fun SetupScreen(onReady: () -> Unit) {
    val vm: SetupViewModel = viewModel()
    val state by vm.state.collectAsState()
    var token by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is DownloadState.Done) onReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Asistente para personas ciegas", fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "La primera vez se descarga el modelo de IA (~2 GB).\n" +
            "Necesitás un token de HuggingFace con acceso a Gemma 4.\n\n" +
            "Obtené tu token en: huggingface.co/settings/tokens\n" +
            "Aceptá la licencia en: huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token HuggingFace (hf_...)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state !is DownloadState.Downloading
        )
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is DownloadState.Idle, is DownloadState.Error -> {
                if (s is DownloadState.Error) {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = { vm.startDownload(token) },
                    enabled = token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (s is DownloadState.Error) "Reintentar" else "Descargar modelo")
                }
            }

            is DownloadState.Downloading -> {
                val mb = s.bytesDownloaded / (1024 * 1024)
                val totalMb = if (s.totalBytes > 0) s.totalBytes / (1024 * 1024) else 0L
                Text(
                    text = if (s.totalBytes > 0) "$mb MB / $totalMb MB" else "$mb MB descargados…",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                if (s.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "No cierres la app durante la descarga",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            is DownloadState.Done -> {
                Text("¡Descarga completa! Iniciando asistente…", textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

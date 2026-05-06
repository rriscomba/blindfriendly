package com.blindfriend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindfriend.viewmodel.AssistantState
import com.blindfriend.viewmodel.AssistantViewModel

@Composable
fun HomeScreen() {
    val vm: AssistantViewModel = viewModel()
    val state by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        vm.bindCamera(lifecycle)
        vm.onAppReady()
    }

    val label = when (state) {
        is AssistantState.Booting -> "Cargando…"
        is AssistantState.Greeting -> "Saludando"
        is AssistantState.Listening -> "Escuchando…"
        is AssistantState.Classifying -> "Pensando"
        is AssistantState.Analyzing -> "Analizando"
        is AssistantState.Continuous -> "Guiando"
        is AssistantState.Error -> "Error"
    }

    Surface(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

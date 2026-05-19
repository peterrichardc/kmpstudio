package studio.kmp.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import studio.kmp.frontend.interop.videoConnect
import studio.kmp.frontend.interop.videoDisconnect
import studio.kmp.frontend.interop.videoInit
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ws.WsState

@Composable
fun EmulatorCanvas(
    wsState:    WsState,
    agentPort:  Int,
    deviceId:   String? = null,
    workDir:    String? = null,
    modifier:   Modifier = Modifier
) {
    val canvasId = remember { "kmp-emulator-canvas" }
    var streaming by remember { mutableStateOf(false) }

    // Reconnect whenever agent connection state or target device changes
    LaunchedEffect(wsState, deviceId) {
        if (wsState == WsState.CONNECTED) {
            if (streaming) { videoDisconnect(); streaming = false }
            val url = buildString {
                append("ws://127.0.0.1:$agentPort/ws/video")
                var sep = '?'
                deviceId?.let { append("${sep}device=$it"); sep = '&' }
                workDir?.let { append("${sep}workDir=${it}"); sep = '&' }
            }
            videoInit(canvasId)
            videoConnect(url, canvasId)
            streaming = true
        } else if (wsState == WsState.DISCONNECTED && streaming) {
            videoDisconnect()
            streaming = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { videoDisconnect() }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            wsState == WsState.DISCONNECTED || wsState == WsState.CONNECTING -> {
                EmulatorPlaceholder(
                    icon    = if (wsState == WsState.CONNECTING) "o" else "[ ]",
                    message = if (wsState == WsState.CONNECTING) "Connecting to agent..." else "Agent offline",
                    hint    = "Run  ./gradlew :agent:run  to start the local agent"
                )
            }
            wsState == WsState.ERROR -> {
                EmulatorPlaceholder(icon = "x", message = "Connection error", hint = "Check agent port in Settings")
            }
            else -> {
                // The actual canvas is a DOM element injected by kmpVideoInit / kmpVideoConnect.
                // We show a placeholder until the first frame arrives.
                EmulatorPlaceholder(icon = ">", message = "Waiting for emulator frame...", hint = "")
            }
        }
    }
}

@Composable
private fun EmulatorPlaceholder(icon: String, message: String, hint: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        // Stylised phone outline
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(KmpSurface0),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, color = KmpOverlay0, fontSize = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("Android", color = KmpOverlay0, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(message, color = KmpSubtext, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(hint, color = KmpOverlay0, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

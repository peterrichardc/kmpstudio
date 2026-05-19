package studio.kmp.frontend

import androidx.compose.runtime.*
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.theme.KmpStudioTheme
import studio.kmp.frontend.ui.*
import studio.kmp.frontend.ws.WsClient

enum class Screen { HOME, IDE, SETTINGS }

@Composable
fun App() {
    val wsClient = remember { WsClient() }
    var screen   by remember { mutableStateOf(Screen.HOME) }
    var project  by remember { mutableStateOf<ProjectState?>(null) }

    DisposableEffect(Unit) {
        onDispose { wsClient.dispose() }
    }

    KmpStudioTheme {
        when (screen) {
            Screen.HOME -> HomeScreen(
                wsClient       = wsClient,
                onProjectReady = { p ->
                    project = p
                    if (wsClient.state.value != studio.kmp.frontend.ws.WsState.CONNECTED)
                        wsClient.connect(SettingsStorage.agentPort)
                    screen = Screen.IDE
                },
                onSettings = { screen = Screen.SETTINGS }
            )
            Screen.IDE -> {
                val p = project
                if (p == null) {
                    LaunchedEffect(Unit) { screen = Screen.HOME }
                } else {
                    IdeScreen(
                        project  = p,
                        wsClient = wsClient,
                        onBack   = { wsClient.disconnect(); screen = Screen.HOME }
                    )
                }
            }
            Screen.SETTINGS -> SettingsScreen(
                onBack = { screen = Screen.HOME }
            )
        }
    }
}

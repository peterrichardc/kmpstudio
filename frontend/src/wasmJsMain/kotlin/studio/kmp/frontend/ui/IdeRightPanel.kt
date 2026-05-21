package studio.kmp.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import studio.kmp.frontend.interop.monacoSetValue
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ws.WsState
import studio.kmp.shared.model.*

@Composable
fun IdeRightPanel(
    state:             IdeState,
    wsState:           WsState,
    onInterpretWithAi: (String) -> Unit,
    onFixWithAi:       (String) -> Unit,
    modifier:          Modifier = Modifier
) {
    if (state.rightExpanded) {
        Column(modifier = modifier) {
            RightTabBar(
                state    = state,
                onClose  = { state.rightExpanded = false }
            )
            when (state.rightTab) {
                RightTab.LOGS -> LogPanel(
                    entries           = state.logs,
                    onClear           = { state.logs.clear() },
                    modifier          = Modifier.fillMaxSize(),
                    isAiProcessing    = state.isAiStreaming,
                    onInterpretWithAi = onInterpretWithAi,
                    onFixWithAi       = onFixWithAi
                )
                RightTab.LOGCAT -> LogPanel(
                    entries  = state.logcatLogs,
                    onClear  = { state.logcatLogs.clear() },
                    modifier = Modifier.fillMaxSize()
                )
                RightTab.HISTORY -> DiffHistoryPanel(
                    entries  = state.diffHistory,
                    onRevert = { entry ->
                        if (entry.filePath == state.editorState.activeFile) {
                            monacoSetValue(state.editorId, entry.originalContent)
                            state.editorState.updateContent(entry.filePath, entry.originalContent)
                        } else {
                            state.editorState.openFiles[entry.filePath] = entry.originalContent
                        }
                        state.wsClient.send(WsMessage.WriteFile(entry.filePath, entry.originalContent))
                        state.diffHistory.remove(entry)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                RightTab.EMULATOR -> EmulatorPanel(
                    wsState           = wsState,
                    agentPort         = state.agentPort,
                    avds              = state.avdList,
                    emulatorStatus    = state.emulatorStatus,
                    activeSerial      = state.activeDeviceSerial,
                    projectPath       = state.project.path,
                    showNewDialog     = state.showNewAvdDialog,
                    onNewDialogChange = { state.showNewAvdDialog = it },
                    onStartEmulator   = { avdName -> state.sendEmulatorCmd(CommandType.START_EMULATOR, avdName) },
                    onStopEmulator    = { serial  -> state.sendEmulatorCmd(CommandType.STOP_EMULATOR, serial) },
                    onCreateAvd       = { name    ->
                        state.sendEmulatorCmd(CommandType.CREATE_AVD, name)
                        state.rightTab = RightTab.LOGS
                    },
                    onConnectDevice   = { serial -> state.activeDeviceSerial = serial },
                    onRefresh         = { state.wsClient.send(WsMessage.ListAvds()) },
                    modifier          = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .width(28.dp)
                .fillMaxHeight()
                .background(KmpCrust)
                .clickable { state.rightExpanded = true },
            contentAlignment = Alignment.Center
        ) { Text(">", color = KmpOverlay0, fontSize = 14.sp) }
    }
}

@Composable
private fun RightTabBar(state: IdeState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(KmpCrust)
    ) {
        RightTab.entries.forEach { tab ->
            val label = when (tab) {
                RightTab.LOGS     -> "Build"
                RightTab.LOGCAT   -> if (state.logcatCmdId != null) "Logcat (live)" else "Logcat"
                RightTab.EMULATOR -> "Emulator"
                RightTab.HISTORY  -> if (state.diffHistory.isNotEmpty()) "History (${state.diffHistory.size})" else "History"
            }
            RightTabItem(label, state.rightTab == tab) { state.rightTab = tab }
        }
        Spacer(Modifier.weight(1f))
        IconTextButton("x", onClose)
    }
}

@Composable
fun RunTargetDialog(
    targets:        List<String>,
    selectedTarget: String?,
    onSelect:       (String) -> Unit,
    onConfirm:      (String) -> Unit,
    onDismiss:      () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = KmpCrust,
        shape            = RoundedCornerShape(12.dp),
        title = {
            Text("Select Target", color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        },
        text = {
            Column {
                targets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(target) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTarget == target,
                            onClick  = { onSelect(target) },
                            colors   = RadioButtonDefaults.colors(selectedColor = KmpBlue)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(targetLabel(target), color = KmpText, fontSize = 14.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
            ) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick  = { selectedTarget?.let { onConfirm(it) }; onDismiss() },
                enabled  = selectedTarget != null,
                colors   = ButtonDefaults.buttonColors(containerColor = KmpBlue, contentColor = KmpCrust)
            ) { Text("Run", fontWeight = FontWeight.SemiBold) }
        }
    )
}

@Composable
fun RightTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) KmpMantle else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) KmpText else KmpOverlay0,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun IconTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 8.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = KmpOverlay0, fontSize = 11.sp) }
}

internal fun targetLabel(target: String) = when (target) {
    "android" -> "Android"
    "ios"     -> "iOS"
    "desktop" -> "Desktop"
    "web"     -> "Web"
    else      -> target.replaceFirstChar { it.uppercase() }
}

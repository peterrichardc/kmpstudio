package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import studio.kmp.frontend.interop.*
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ws.WsClient
import studio.kmp.frontend.ws.WsState
import studio.kmp.shared.model.*
import kotlin.random.Random

@Composable
fun IdeScreen(
    project:  ProjectState,
    wsClient: WsClient,
    onBack:   () -> Unit
) {
    val state   = rememberIdeState(project, wsClient)
    val wsState by wsClient.state.collectAsState()

    IdeMessageRouter(state)

    SideEffect {
        setEditorVisible(
            state.editorId,
            !state.showRunMenu && !state.showNewAvdDialog &&
            state.pendingPlan == null && state.diffReviewQueue.isEmpty()
        )
    }

    if (state.showRunMenu) {
        RunTargetDialog(
            targets        = project.targets,
            selectedTarget = state.selectedTarget,
            onSelect       = { state.selectedTarget = it },
            onConfirm      = { target ->
                state.sendCommand(CommandType.RUN, target)
                state.rightTab = RightTab.EMULATOR
            },
            onDismiss      = { state.showRunMenu = false }
        )
    }

    val pendingPlan = state.pendingPlan
    if (pendingPlan != null) {
        PlanConfirmDialog(
            plan      = pendingPlan,
            onConfirm = {
                state.pendingAiRequest?.let { req ->
                    wsClient.send(WsMessage.PlanConfirm(
                        prompt         = req.prompt,
                        filePaths      = req.filePaths,
                        provider       = req.provider,
                        apiKey         = req.apiKey,
                        customBaseUrl  = req.customBaseUrl,
                        conversationId = req.conversationId,
                        history        = req.history,
                        projectRoot    = req.projectRoot,
                        planSummary    = pendingPlan.summary,
                        planFilePaths  = pendingPlan.steps.map { it.filePath }
                    ))
                    state.isAiStreaming = true
                }
                state.pendingPlan = null
                state.pendingAiRequest = null
            },
            onCancel = {
                state.chatHistory.add(ChatMessage("assistant", "Cancelled."))
                state.pendingPlan = null
                state.pendingAiRequest = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(KmpBase)) {
            IdeToolbar(
                state   = state,
                wsState = wsState,
                onBack  = onBack
            )

            if (state.showAiBar) {
                ChatPanel(
                    history  = state.chatHistory,
                    value    = state.aiPrompt,
                    onChange = { state.aiPrompt = it },
                    onSend   = {
                        if (state.aiPrompt.isNotBlank()) {
                            val previousHistory = state.chatHistory.toList()
                            state.chatHistory.add(ChatMessage("user", state.aiPrompt))
                            val req = WsMessage.AiRequest(
                                prompt         = state.aiPrompt,
                                filePaths      = listOfNotNull(state.editorState.activeFile),
                                provider       = SettingsStorage.aiProvider.name,
                                apiKey         = SettingsStorage.activeApiKey(),
                                customBaseUrl  = SettingsStorage.customBaseUrl.ifBlank { null },
                                conversationId = state.conversationId,
                                history        = previousHistory,
                                projectRoot    = project.path
                            )
                            state.pendingAiRequest = req
                            wsClient.send(req)
                            state.isAiStreaming = true
                            state.aiPrompt = ""
                        }
                    },
                    onClear      = { state.chatHistory.clear() },
                    isProcessing = state.isAiStreaming
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.treeVisible) {
                    FileTree(
                        rootPath     = project.path,
                        dirListings  = state.dirListings,
                        expandedDirs = state.expandedDirs,
                        selectedFile = state.editorState.activeFile,
                        onExpandDir  = { path ->
                            state.expandedDirs = if (path in state.expandedDirs) {
                                state.expandedDirs - path
                            } else {
                                wsClient.send(WsMessage.ListDir(path))
                                state.expandedDirs + path
                            }
                        },
                        onSelectFile = state::switchToFile,
                        modifier     = Modifier.width(220.dp).fillMaxHeight()
                    )
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KmpSurface0))
                }

                IdeRightPanel(
                    state             = state,
                    wsState           = wsState,
                    modifier          = if (state.rightTab == RightTab.EMULATOR)
                        Modifier.weight(1f).fillMaxHeight()
                    else
                        Modifier.width(340.dp).fillMaxHeight(),
                    onInterpretWithAi = { logText ->
                        state.interpretingLog = true
                        state.isAiStreaming   = true
                        wsClient.send(WsMessage.AiRequest(
                            prompt = buildString {
                                appendLine("Analise o output de build abaixo. NÃO gere código nem blocos DIFF.")
                                appendLine()
                                appendLine("Faça o seguinte em português:")
                                appendLine("1. Liste cada erro com: arquivo, linha e mensagem resumida")
                                appendLine("2. Liste os avisos mais relevantes (se houver)")
                                appendLine("3. Escreva um resumo curto da causa da falha e como corrigir")
                                appendLine()
                                appendLine("OUTPUT DO BUILD:")
                                appendLine("```")
                                append(logText)
                                appendLine()
                                append("```")
                            },
                            filePaths      = emptyList(),
                            provider       = SettingsStorage.aiProvider.name,
                            apiKey         = SettingsStorage.activeApiKey(),
                            customBaseUrl  = SettingsStorage.customBaseUrl.ifBlank { null },
                            conversationId = state.conversationId,
                            history        = emptyList(),
                            projectRoot    = project.path,
                            noPlan         = true
                        ))
                    },
                    onFixWithAi = { errorText ->
                        val prompt          = "Fix this build error:\n\n$errorText"
                        val previousHistory = state.chatHistory.toList()
                        state.chatHistory.add(ChatMessage("user", prompt))
                        val req = WsMessage.AiRequest(
                            prompt         = prompt,
                            filePaths      = listOfNotNull(state.editorState.activeFile),
                            provider       = SettingsStorage.aiProvider.name,
                            apiKey         = SettingsStorage.activeApiKey(),
                            customBaseUrl  = SettingsStorage.customBaseUrl.ifBlank { null },
                            conversationId = state.conversationId,
                            history        = previousHistory,
                            projectRoot    = project.path,
                            noPlan         = true
                        )
                        state.pendingAiRequest = req
                        wsClient.send(req)
                        state.isAiStreaming = true
                        state.showAiBar = true
                    }
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KmpSurface0))

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (state.editorState.openFiles.isNotEmpty()) {
                        EditorTabBar(
                            files      = state.editorState.openFiles.keys.toList(),
                            activeFile = state.editorState.activeFile,
                            nameOf     = state.editorState::fileName,
                            onSelect   = state::switchToFile,
                            onClose    = { path ->
                                state.editorState.activeFile?.let { c ->
                                    wsClient.send(WsMessage.WriteFile(c, monacoGetValue(state.editorId)))
                                }
                                state.editorState.closeFile(path)
                                state.editorState.activeFile?.let { next ->
                                    monacoSetValue(state.editorId, state.editorState.openFiles[next] ?: "")
                                }
                            },
                            onGenerateTests = {
                                val selection  = monacoGetSelection(state.editorId).trim()
                                val codeToTest = selection.ifBlank { monacoGetValue(state.editorId) }
                                val scope      = if (selection.isNotBlank()) "selected code" else "this file"
                                val prompt     = buildString {
                                    appendLine("Generate unit tests (@Test) for the following Kotlin code.")
                                    appendLine("Place tests in the appropriate test source set (commonTest, androidUnitTest, etc.).")
                                    appendLine("Use kotlin.test for assertions. Generate only the @Test functions — no explanation.")
                                    appendLine()
                                    appendLine("```kotlin")
                                    append(codeToTest)
                                    appendLine("```")
                                }
                                val previousHistory = state.chatHistory.toList()
                                state.chatHistory.add(ChatMessage("user", "Generate tests for $scope"))
                                val req = WsMessage.AiRequest(
                                    prompt         = prompt,
                                    filePaths      = listOfNotNull(state.editorState.activeFile),
                                    provider       = SettingsStorage.aiProvider.name,
                                    apiKey         = SettingsStorage.activeApiKey(),
                                    customBaseUrl  = SettingsStorage.customBaseUrl.ifBlank { null },
                                    conversationId = state.conversationId,
                                    history        = previousHistory,
                                    projectRoot    = project.path
                                )
                                state.pendingAiRequest = req
                                wsClient.send(req)
                                state.isAiStreaming = true
                                state.showAiBar = true
                            }
                        )
                    }
                    EditorPanel(
                        editorId       = state.editorId,
                        initialContent = "",
                        modifier       = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        if (state.showSearchPanel) {
            SemanticSearchPanel(
                query         = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                onSearch      = {
                    if (state.searchQuery.isNotBlank()) {
                        state.isSearching = true
                        state.searchResults = emptyList()
                        wsClient.send(WsMessage.SemanticSearchRequest(
                            query         = state.searchQuery,
                            projectRoot   = project.path,
                            provider      = SettingsStorage.aiProvider.name,
                            apiKey        = SettingsStorage.activeApiKey(),
                            customBaseUrl = SettingsStorage.customBaseUrl.ifBlank { null }
                        ))
                    }
                },
                isSearching   = state.isSearching,
                results       = state.searchResults,
                onResultClick = { hit ->
                    val absolutePath = if (hit.filePath.startsWith("/")) hit.filePath
                        else "${project.path}/${hit.filePath}"
                    val alreadyOpen = absolutePath in state.editorState.openFiles ||
                        absolutePath == state.editorState.activeFile
                    if (alreadyOpen) {
                        state.switchToFile(absolutePath)
                        monacoGoToLine(state.editorId, hit.lineNumber)
                    } else {
                        state.pendingGoToLine = hit.lineNumber
                        state.switchToFile(absolutePath)
                    }
                    state.showSearchPanel = false
                },
                onDismiss = { state.showSearchPanel = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdeToolbar(state: IdeState, wsState: WsState, onBack: () -> Unit) {
    Toolbar(
        projectName    = state.project.name,
        wsState        = wsState,
        treeVisible    = state.treeVisible,
        targets        = state.project.targets,
        onBack         = onBack,
        onBuild        = { state.sendCommand(CommandType.BUILD) },
        onRunSingle    = { target ->
            state.sendCommand(CommandType.RUN, target)
            state.rightTab = RightTab.EMULATOR
        },
        onOpenRunMenu  = {
            state.selectedTarget = state.project.targets.firstOrNull()
            state.showRunMenu = true
        },
        onLogcat       = {
            val cmdId = state.logcatCmdId
            if (cmdId != null) {
                state.wsClient.send(WsMessage.Command(cmdId, CommandType.STOP))
                state.logcatCmdId = null
            } else {
                val id = Random.nextLong().toULong().toString(16).take(8)
                state.logcatCmdId = id
                state.wsClient.send(WsMessage.Command(id, CommandType.LOGCAT, emptyList(), state.project.path))
                state.rightTab = RightTab.LOGCAT
            }
        },
        onStopAll      = { state.wsClient.send(WsMessage.Command("stop-all", CommandType.STOP)) },
        logcatActive   = state.logcatCmdId != null,
        onAiToggle     = { state.showAiBar = !state.showAiBar },
        onTreeToggle   = { state.treeVisible = !state.treeVisible },
        onRefreshTree  = { state.wsClient.send(WsMessage.ListDir(state.project.path)) },
        aiActive       = state.showAiBar,
        onSearchToggle = {
            state.showSearchPanel = !state.showSearchPanel
            if (state.showSearchPanel) state.searchResults = emptyList()
        },
        searchActive   = state.showSearchPanel
    )
}

@Composable
private fun Toolbar(
    projectName:    String,
    wsState:        WsState,
    treeVisible:    Boolean,
    targets:        List<String>,
    onBack:         () -> Unit,
    onBuild:        () -> Unit,
    onRunSingle:    (String) -> Unit,
    onOpenRunMenu:  () -> Unit,
    onLogcat:       () -> Unit,
    onStopAll:      () -> Unit,
    logcatActive:   Boolean,
    onAiToggle:     () -> Unit,
    onTreeToggle:   () -> Unit,
    onRefreshTree:  () -> Unit,
    aiActive:       Boolean,
    onSearchToggle: () -> Unit = {},
    searchActive:   Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(KmpMantle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)) {
            Text("<", fontSize = 16.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (treeVisible) KmpSurface0 else Color.Transparent)
                .clickable(onClick = onTreeToggle)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("[ ]", color = KmpOverlay0, fontSize = 11.sp) }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onRefreshTree)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("Refresh", color = KmpOverlay0, fontSize = 11.sp) }
        Spacer(Modifier.width(8.dp))
        Text(projectName, color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))

        Spacer(Modifier.weight(1f))

        ToolbarButton("Build",  KmpGreen,  onClick = onBuild)
        Spacer(Modifier.width(4.dp))
        ToolbarButton("Run", KmpBlue, onClick = {
            if (targets.size == 1) onRunSingle(targets.first())
            else onOpenRunMenu()
        })
        Spacer(Modifier.width(4.dp))
        ToolbarButton("Logcat", KmpPeach, active = logcatActive, onClick = onLogcat)
        Spacer(Modifier.width(4.dp))
        ToolbarButton("Stop",   KmpRed,   onClick = onStopAll)

        Spacer(Modifier.width(12.dp))
        VerticalDivider(modifier = Modifier.height(20.dp), color = KmpSurface0)
        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (searchActive) KmpTeal.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClick = onSearchToggle)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("Search", color = if (searchActive) KmpTeal else KmpOverlay0,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (aiActive) KmpPurple.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClick = onAiToggle)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("AI", color = if (aiActive) KmpPurple else KmpOverlay0,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        ConnectionDot(wsState)
    }
}

@Composable
private fun EditorTabBar(
    files:           List<String>,
    activeFile:      String?,
    nameOf:          (String) -> String,
    onSelect:        (String) -> Unit,
    onClose:         (String) -> Unit,
    onGenerateTests: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(KmpCrust)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
        ) {
            files.forEach { path ->
                val active = path == activeFile
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (active) KmpMantle else Color.Transparent)
                        .clickable { onSelect(path) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nameOf(path),
                        color    = if (active) KmpText else KmpOverlay0,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onClose(path) },
                        contentAlignment = Alignment.Center
                    ) { Text("x", color = KmpOverlay0, fontSize = 10.sp) }
                }
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KmpSurface0))
            }
        }
        if (onGenerateTests != null) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KmpSurface0))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onGenerateTests)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Gen Tests", color = KmpPurple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PlanConfirmDialog(
    plan:      WsMessage.PlanSuggestion,
    onConfirm: () -> Unit,
    onCancel:  () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor   = KmpCrust,
        shape            = RoundedCornerShape(12.dp),
        title = {
            Text("Plan", color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.summary, color = KmpSubtext, fontSize = 13.sp)
                if (plan.steps.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KmpSurface0))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.steps.forEach { step ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(step.filePath, color = KmpBlue, fontSize = 11.sp)
                                if (step.description.isNotBlank()) {
                                    Text(step.description, color = KmpOverlay0, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
            ) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = KmpPurple, contentColor = KmpCrust)
            ) { Text("Apply Changes", fontWeight = FontWeight.SemiBold) }
        }
    )
}

@Composable
private fun ToolbarButton(label: String, color: Color, active: Boolean = false, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active || hovered) color.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (active || hovered) color else KmpSubtext,
            fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ConnectionDot(state: WsState) {
    val (color, text) = when (state) {
        WsState.CONNECTED    -> KmpGreen   to "Agent"
        WsState.CONNECTING   -> KmpYellow  to "Connecting"
        WsState.ERROR        -> KmpRed     to "Error"
        WsState.DISCONNECTED -> KmpOverlay0 to "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 11.sp)
    }
}

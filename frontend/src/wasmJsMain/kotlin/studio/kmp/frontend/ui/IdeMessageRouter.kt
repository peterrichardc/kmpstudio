package studio.kmp.frontend.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.kmp.frontend.interop.*
import studio.kmp.frontend.ws.WsState
import studio.kmp.shared.model.*
import studio.kmp.shared.parser.LineType

@Composable
fun IdeMessageRouter(state: IdeState) {
    val wsState by state.wsClient.state.collectAsState()
    val scope   = rememberCoroutineScope()

    LaunchedEffect(state.wsClient) {
        state.wsClient.messages.collect { msg ->
            when (msg) {
                is WsMessage.Output -> {
                    val isLogcat = msg.commandId == state.logcatCmdId
                    if (msg.chunk.isNotBlank()) {
                        val p = state.parser.classify(msg.chunk)
                        val entry = LogEntry(p.raw, p.type,
                            if (msg.stream == StreamType.STDERR) StreamType.STDERR else StreamType.STDOUT)
                        if (isLogcat) state.logcatLogs.add(entry) else state.logs.add(entry)
                        if (!isLogcat) {
                            p.buildError?.let { err ->
                                state.buildErrors.getOrPut(err.filePath) { mutableListOf() }.add(err)
                                if (state.editorState.activeFile == err.filePath)
                                    state.applyMarkersForFile(err.filePath)
                            }
                        }
                    }
                    if (msg.done) {
                        if (isLogcat) state.logcatCmdId = null else state.rightTab = RightTab.LOGS
                    }
                }
                is WsMessage.DiffSuggestion -> {
                    val resolved = resolveDiffPath(msg.filePath, state.project.path)
                    if (msg.autoApplied) {
                        when {
                            resolved == state.editorState.activeFile -> {
                                val updated = applyHunksToContent(monacoGetValue(state.editorId), msg.hunks)
                                monacoSetValue(state.editorId, updated)
                                state.editorState.updateContent(resolved, updated)
                            }
                            resolved in state.editorState.openFiles -> {
                                val current = state.editorState.openFiles[resolved] ?: return@collect
                                val updated = applyHunksToContent(current, msg.hunks)
                                state.editorState.openFiles[resolved] = updated
                            }
                        }
                    } else {
                        val original = when {
                            resolved == state.editorState.activeFile -> monacoGetValue(state.editorId)
                            resolved in state.editorState.openFiles  -> state.editorState.openFiles[resolved]
                            else -> null
                        }
                        if (original != null) {
                            val proposed = applyHunksToContent(original, msg.hunks)
                            state.diffReviewQueue.add(DiffReviewState(resolved, original, proposed))
                        } else {
                            state.pendingDiffs[resolved] = msg.hunks
                            state.diffLoadPending.add(resolved)
                            state.wsClient.send(WsMessage.ReadFile(resolved))
                            scope.launch {
                                delay(30_000)
                                if (state.diffLoadPending.remove(resolved)) {
                                    state.pendingDiffs.remove(resolved)
                                }
                            }
                        }
                    }
                }
                is WsMessage.AgentLoopStatus -> {
                    val isDone = msg.phase == "success" || msg.phase == "failed"
                    state.isAgentLoopRunning = !isDone
                    if (isDone) state.isAiStreaming = false
                    if (msg.phase == "applying" && msg.attempt == 1) {
                        state.buildErrors.clear()
                        monacoSetMarkers(state.editorId, "[]")
                    }
                    val entry: LogEntry? = when (msg.phase) {
                        "applying" -> LogEntry("${msg.message} (${msg.attempt}/${msg.maxAttempts})", LineType.INFO)
                        "building" -> LogEntry("Building (${msg.attempt}/${msg.maxAttempts})...", LineType.INFO)
                        "retrying" -> LogEntry(msg.message, LineType.WARNING)
                        "success"  -> if (msg.message.isNotBlank()) LogEntry(msg.message, LineType.SUCCESS) else null
                        "failed"   -> LogEntry(msg.message.ifBlank { "Build failed" }, LineType.ERROR)
                        else       -> if (msg.message.isNotBlank()) LogEntry(msg.message, LineType.INFO) else null
                    }
                    entry?.let { state.logs.add(it); state.rightTab = RightTab.LOGS }
                }
                is WsMessage.PlanSuggestion -> {
                    state.isAiStreaming = false
                    state.pendingPlan = msg
                }
                is WsMessage.AiResponse -> {
                    if (!state.isAgentLoopRunning) state.isAiStreaming = false
                    if (state.interpretingLog) {
                        state.interpretingLog = false
                        val sep = "=".repeat(52)
                        state.logs.add(LogEntry(sep, LineType.INFO))
                        state.logs.add(LogEntry("  Análise da IA", LineType.SUCCESS))
                        state.logs.add(LogEntry(sep, LineType.INFO))
                        msg.text.trim().lines().forEach { line ->
                            state.logs.add(LogEntry(line, LineType.SUCCESS))
                        }
                        state.logs.add(LogEntry(sep, LineType.INFO))
                    } else {
                        val explanation = stripDiffBlocks(msg.text).trim()
                        state.chatHistory.add(ChatMessage("assistant",
                            explanation.ifBlank {
                                if (state.diffReviewQueue.isNotEmpty()) "Changes ready for review." else "Done."
                            }
                        ))
                    }
                }
                is WsMessage.ErrorMsg -> {
                    state.isAgentLoopRunning = false
                    state.isAiStreaming = false
                    state.logs.add(LogEntry("ERROR: ${msg.message}", LineType.ERROR))
                }
                is WsMessage.SessionInfo ->
                    state.logs.add(LogEntry(
                        "Agent v${msg.agentVersion} | Android CLI ${msg.androidCliVersion}",
                        LineType.SUCCESS
                    ))
                is WsMessage.DirListing -> {
                    consoleLog("[KMP] DirListing received: ${msg.path} — ${msg.entries.size} entries")
                    state.dirListings[msg.path] = msg.entries
                }
                is WsMessage.FileContent -> {
                    val isForDiff    = state.diffLoadPending.remove(msg.path)
                    val pendingHunks = state.pendingDiffs.remove(msg.path)
                    if (isForDiff && pendingHunks != null) {
                        val proposed = applyHunksToContent(msg.content, pendingHunks)
                        state.diffReviewQueue.add(DiffReviewState(msg.path, msg.content, proposed))
                    } else {
                        state.editorState.openFile(msg.path, msg.content)
                        monacoSetValue(state.editorId, msg.content)
                        state.applyMarkersForFile(msg.path)
                        state.pendingGoToLine?.let { line ->
                            monacoGoToLine(state.editorId, line)
                            state.pendingGoToLine = null
                        }
                    }
                }
                is WsMessage.ScaffoldProgress -> {
                    state.logs.add(LogEntry(
                        if (msg.error != null) "Error: ${msg.error}" else "Created: ${msg.filePath}",
                        if (msg.error != null) LineType.ERROR else LineType.SUCCESS
                    ))
                    if (msg.done) {
                        state.wsClient.send(WsMessage.ListDir(state.project.path))
                        state.rightTab = RightTab.LOGS
                    }
                }
                is WsMessage.AvdList -> state.avdList = msg.avds
                is WsMessage.EmulatorStatus -> {
                    state.emulatorStatus = msg
                    val logMsg = when (msg.phase) {
                        "ready"   -> "Emulator ${msg.serial} ready"
                        "stopped" -> "Emulator stopped"
                        "error"   -> "Emulator error: ${msg.message}"
                        else      -> msg.message ?: msg.phase
                    }
                    val logType = when (msg.phase) {
                        "ready"  -> LineType.SUCCESS
                        "error"  -> LineType.ERROR
                        else     -> LineType.INFO
                    }
                    state.logs.add(LogEntry(logMsg, logType))
                    when (msg.phase) {
                        "ready" -> {
                            state.activeDeviceSerial = msg.serial
                            state.rightTab = RightTab.EMULATOR
                            state.wsClient.send(WsMessage.ListAvds())
                        }
                        "stopped" -> {
                            state.activeDeviceSerial = null
                            state.wsClient.send(WsMessage.ListAvds())
                        }
                    }
                }
                is WsMessage.SemanticSearchResult -> {
                    state.searchResults = msg.hits
                    state.isSearching = false
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (state.diffReviewQueue.isNotEmpty()) {
                val batchTotal = state.diffReviewQueue.size
                var batchIndex = 0
                while (state.diffReviewQueue.isNotEmpty()) {
                    batchIndex++
                    val item = state.diffReviewQueue.first()
                    showDiffPreview(
                        item.filePath.substringAfterLast('/'),
                        item.originalContent,
                        item.proposedContent,
                        batchIndex,
                        batchTotal
                    )
                    while (true) {
                        delay(150)
                        val result = popDiffResult()
                        if (result != null) {
                            if (result == "accept") {
                                when {
                                    item.filePath == state.editorState.activeFile -> {
                                        monacoSetValue(state.editorId, item.proposedContent)
                                        state.editorState.updateContent(item.filePath, item.proposedContent)
                                    }
                                    item.filePath in state.editorState.openFiles ->
                                        state.editorState.openFiles[item.filePath] = item.proposedContent
                                }
                                state.wsClient.send(WsMessage.WriteFile(item.filePath, item.proposedContent))
                            }
                            state.diffReviewQueue.removeAt(0)
                            break
                        }
                    }
                }
            } else {
                delay(200)
            }
        }
    }

    LaunchedEffect(wsState, state.project.path) {
        if (wsState == WsState.CONNECTED) {
            consoleLog("[KMP] CONNECTED — sending ListDir for ${state.project.path}")
            state.wsClient.send(WsMessage.ListDir(state.project.path))
            state.wsClient.send(WsMessage.ListAvds())
        }
    }
}

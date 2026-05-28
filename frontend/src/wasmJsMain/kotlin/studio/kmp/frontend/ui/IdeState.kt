package studio.kmp.frontend.ui

import androidx.compose.runtime.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.kmp.frontend.interop.monacoGetValue
import studio.kmp.frontend.interop.monacoSetMarkers
import studio.kmp.frontend.interop.monacoSetValue
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.ws.WsClient
import studio.kmp.shared.model.*
import studio.kmp.shared.parser.BuildError
import studio.kmp.shared.parser.CliOutputParser
import kotlin.random.Random

enum class RightTab { LOGS, LOGCAT, EMULATOR, HISTORY }

data class DiffReviewState(
    val filePath: String,
    val originalContent: String,
    val proposedContent: String
)

data class DiffHistoryEntry(
    val id: String,
    val filePath: String,
    val fileName: String,
    val timestamp: String,
    val originalContent: String
)

fun resolveDiffPath(relativePath: String, projectRoot: String): String =
    if (relativePath.startsWith("/")) relativePath else "$projectRoot/$relativePath"

fun applyHunksToContent(content: String, hunks: List<DiffHunk>): String {
    val lines = content.lines().toMutableList()
    hunks.sortedByDescending { it.startLine }.forEach { hunk ->
        val start = (hunk.startLine - 1).coerceAtLeast(0)
        val end   = (start + hunk.oldLines.size).coerceAtMost(lines.size)
        repeat(end - start) { lines.removeAt(start) }
        lines.addAll(start, hunk.newLines)
    }
    return lines.joinToString("\n")
}

fun stripDiffBlocks(text: String): String =
    Regex("""DIFF_START.*?DIFF_END""", RegexOption.DOT_MATCHES_ALL)
        .replace(text, "").trim()

class IdeState(
    val project:  ProjectState,
    val wsClient: WsClient,
    val editorId: String = "ide-editor-main"
) {
    val parser      = CliOutputParser()
    val agentPort   = SettingsStorage.agentPort
    val editorState = EditorState()

    val logs        = mutableStateListOf<LogEntry>()
    val logcatLogs  = mutableStateListOf<LogEntry>()
    var logcatCmdId by mutableStateOf<String?>(null)
    var rightTab    by mutableStateOf(RightTab.LOGS)
    var showAiBar   by mutableStateOf(true)
    var aiPrompt    by mutableStateOf("")
    var rightExpanded by mutableStateOf(true)
    var treeVisible   by mutableStateOf(true)

    val buildErrors  = mutableStateMapOf<String, MutableList<BuildError>>()
    val dirListings  = mutableStateMapOf<String, List<FileEntry>>()
    var expandedDirs by mutableStateOf(setOf(project.path))

    val conversationId = Random.nextLong().toULong().toString(16)
    val chatHistory    = mutableStateListOf<ChatMessage>().apply {
        addAll(SettingsStorage.chatHistory(project.path))
    }

    var showRunMenu      by mutableStateOf(false)
    var showNewAvdDialog by mutableStateOf(false)
    var selectedTarget   by mutableStateOf<String?>(null)
    var pendingPlan      by mutableStateOf<WsMessage.PlanSuggestion?>(null)
    var pendingAiRequest by mutableStateOf<WsMessage.AiRequest?>(null)

    var avdList            by mutableStateOf<List<AvdInfo>>(emptyList())
    var emulatorStatus     by mutableStateOf<WsMessage.EmulatorStatus?>(null)
    var activeDeviceSerial by mutableStateOf<String?>(null)
    var isAiStreaming       by mutableStateOf(false)
    var isAgentLoopRunning by mutableStateOf(false)
    var interpretingLog    by mutableStateOf(false)

    var showSearchPanel by mutableStateOf(false)
    var searchQuery     by mutableStateOf("")
    var searchResults   by mutableStateOf<List<SearchHit>>(emptyList())
    var isSearching     by mutableStateOf(false)
    var pendingGoToLine by mutableStateOf<Int?>(null)

    val pendingDiffs    = mutableStateMapOf<String, List<DiffHunk>>()
    val diffLoadPending = mutableSetOf<String>()
    val diffReviewQueue = mutableStateListOf<DiffReviewState>()
    val diffHistory     = mutableStateListOf<DiffHistoryEntry>()

    fun markersJson(errors: List<BuildError>): String = buildString {
        append('[')
        errors.forEachIndexed { i, e ->
            if (i > 0) append(',')
            append("""{"startLine":${e.line},"startCol":${e.col},"endLine":${e.line},"endCol":9999,"message":""")
            append(Json.encodeToString(e.message))
            append(""","severity":"error"}""")
        }
        append(']')
    }

    fun applyMarkersForFile(path: String) {
        val errors = buildErrors[path]
        monacoSetMarkers(editorId, if (errors.isNullOrEmpty()) "[]" else markersJson(errors))
    }

    fun saveActiveFile() {
        editorState.activeFile?.let { path ->
            val content = monacoGetValue(editorId)
            editorState.updateContent(path, content)
            wsClient.send(WsMessage.WriteFile(path, content))
        }
    }

    fun sendCommand(type: CommandType, vararg extra: String) {
        if (type == CommandType.BUILD || type == CommandType.RUN) saveActiveFile()
        if (type == CommandType.BUILD) {
            buildErrors.clear()
            monacoSetMarkers(editorId, "[]")
        }
        val id = Random.nextLong().toULong().toString(16).take(8)
        wsClient.send(WsMessage.Command(id, type, extra.toList(), project.path))
        rightTab = RightTab.LOGS
    }

    fun sendEmulatorCmd(type: CommandType, vararg args: String) {
        val id = Random.nextLong().toULong().toString(16).take(8)
        wsClient.send(WsMessage.Command(id, type, args.toList(), null))
    }

    fun switchToFile(path: String) {
        editorState.activeFile?.let { current ->
            val content = monacoGetValue(editorId)
            editorState.updateContent(current, content)
            wsClient.send(WsMessage.WriteFile(current, content))
        }
        if (path in editorState.openFiles) {
            editorState.activeFile = path
            monacoSetValue(editorId, editorState.openFiles[path] ?: "")
            applyMarkersForFile(path)
        } else {
            wsClient.send(WsMessage.ReadFile(path))
        }
    }
}

@Composable
fun rememberIdeState(project: ProjectState, wsClient: WsClient): IdeState =
    remember(project, wsClient) { IdeState(project, wsClient) }

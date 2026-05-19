package studio.kmp.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class WsMessage {

    @Serializable @SerialName("command")
    data class Command(
        val id: String,
        val type: CommandType,
        val args: List<String> = emptyList(),
        val workDir: String? = null
    ) : WsMessage()

    @Serializable @SerialName("output")
    data class Output(
        val commandId: String,
        val chunk: String,
        val stream: StreamType = StreamType.STDOUT,
        val done: Boolean = false
    ) : WsMessage()

    @Serializable @SerialName("ai_request")
    data class AiRequest(
        val prompt: String,
        val filePaths: List<String>,
        val provider: String = "CLAUDE",
        val apiKey: String = "",
        val customBaseUrl: String? = null,
        val conversationId: String = "",
        val history: List<ChatMessage> = emptyList(),
        val projectRoot: String? = null,
        val noPlan: Boolean = false
    ) : WsMessage()

    @Serializable @SerialName("ai_response")
    data class AiResponse(
        val conversationId: String,
        val text: String
    ) : WsMessage()

    @Serializable @SerialName("diff_suggestion")
    data class DiffSuggestion(
        val filePath: String,
        val hunks: List<DiffHunk>,
        val autoApplied: Boolean = false   // true = agent wrote to disk; frontend skips the review dialog
    ) : WsMessage()

    @Serializable @SerialName("plan_suggestion")
    data class PlanSuggestion(
        val conversationId: String,
        val summary: String,
        val steps: List<PlanStep>
    ) : WsMessage()

    @Serializable @SerialName("plan_confirm")
    data class PlanConfirm(
        val prompt: String,
        val filePaths: List<String>,
        val provider: String = "CLAUDE",
        val apiKey: String = "",
        val customBaseUrl: String? = null,
        val conversationId: String = "",
        val history: List<ChatMessage> = emptyList(),
        val projectRoot: String? = null,
        val planSummary: String = "",
        val planFilePaths: List<String> = emptyList()
    ) : WsMessage()

    @Serializable @SerialName("error")
    data class ErrorMsg(val message: String) : WsMessage()

    @Serializable @SerialName("session_info")
    data class SessionInfo(
        val agentVersion: String,
        val androidCliVersion: String
    ) : WsMessage()

    // ── File system ───────────────────────────────────────────────────────────

    @Serializable @SerialName("list_dir")
    data class ListDir(val path: String) : WsMessage()

    @Serializable @SerialName("dir_listing")
    data class DirListing(val path: String, val entries: List<FileEntry>) : WsMessage()

    @Serializable @SerialName("read_file")
    data class ReadFile(val path: String) : WsMessage()

    @Serializable @SerialName("file_content")
    data class FileContent(val path: String, val content: String) : WsMessage()

    @Serializable @SerialName("write_file")
    data class WriteFile(val path: String, val content: String) : WsMessage()

    // ── AI-powered scaffold ───────────────────────────────────────────────────

    @Serializable @SerialName("ai_scaffold_request")
    data class AiScaffoldRequest(
        val description: String,
        val projectName: String = "",
        val parentDir: String,
        val provider: String = "CLAUDE",
        val apiKey: String = "",
        val customBaseUrl: String? = null
    ) : WsMessage()

    @Serializable @SerialName("ai_scaffold_config")
    data class AiScaffoldConfig(
        val projectName: String,
        val packageName: String,
        val targets: List<String>,
        val architecture: String,
        val libraries: List<String>,
        val reasoning: String
    ) : WsMessage()

    // ── Project scaffolding ───────────────────────────────────────────────────

    @Serializable @SerialName("scaffold_request")
    data class ScaffoldRequest(
        val projectName: String,
        val parentDir: String,
        val packageName: String,
        val targets: List<String>,
        val architecture: String,
        val libraries: List<String>
    ) : WsMessage()

    @Serializable @SerialName("scaffold_progress")
    data class ScaffoldProgress(
        val filePath: String,
        val done: Boolean = false,
        val error: String? = null
    ) : WsMessage()

    // ── Emulator management ───────────────────────────────────────────────────

    @Serializable @SerialName("list_avds")
    class ListAvds : WsMessage()

    @Serializable @SerialName("avd_list")
    data class AvdList(val avds: List<AvdInfo>) : WsMessage()

    @Serializable @SerialName("emulator_status")
    data class EmulatorStatus(
        val phase: String,         // starting | booting | ready | stopped | error
        val avdName: String? = null,
        val serial: String? = null,
        val message: String? = null
    ) : WsMessage()

    // ── Semantic search ───────────────────────────────────────────────────────

    @Serializable @SerialName("semantic_search_request")
    data class SemanticSearchRequest(
        val query: String,
        val projectRoot: String,
        val provider: String = "CLAUDE",
        val apiKey: String = "",
        val customBaseUrl: String? = null
    ) : WsMessage()

    @Serializable @SerialName("semantic_search_result")
    data class SemanticSearchResult(
        val query: String,
        val hits: List<SearchHit>
    ) : WsMessage()

    // ── Agentic loop ──────────────────────────────────────────────────────────

    @Serializable @SerialName("agent_loop_status")
    data class AgentLoopStatus(
        val conversationId: String,
        val attempt: Int,
        val maxAttempts: Int,
        val phase: String,   // "applying" | "building" | "retrying" | "success" | "failed"
        val message: String = ""
    ) : WsMessage()
}

enum class CommandType { BUILD, RUN, LOGCAT, STOP, VERSION, LIST_AVDS, START_EMULATOR, STOP_EMULATOR, CREATE_AVD }
enum class StreamType   { STDOUT, STDERR }

@Serializable
data class ChatMessage(
    val role: String,      // "user" | "assistant"
    val content: String
)

@Serializable
data class AvdInfo(
    val name: String,
    val serial: String? = null,
    val running: Boolean = false
)

@Serializable
data class DiffHunk(
    val startLine: Int,
    val oldLines: List<String>,
    val newLines: List<String>
)

@Serializable
data class PlanStep(
    val filePath: String,
    val description: String
)

@Serializable
data class SearchHit(
    val filePath: String,
    val lineNumber: Int,
    val snippet: String,
    val explanation: String = ""
)

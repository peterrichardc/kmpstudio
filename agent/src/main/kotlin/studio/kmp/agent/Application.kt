package studio.kmp.agent

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ktor.http.*
import kotlinx.serialization.json.*
import studio.kmp.agent.ai.AiProxy
import studio.kmp.agent.executor.CliExecutor
import studio.kmp.agent.executor.EmulatorManager
import studio.kmp.agent.executor.FileSystemAgent
import studio.kmp.agent.scaffold.ProjectScaffolder
import studio.kmp.agent.scaffold.TemplateContext
import studio.kmp.agent.search.SemanticSearch
import studio.kmp.agent.ws.CommandRouter
import studio.kmp.agent.ws.SessionManager
import studio.kmp.shared.model.*
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$")

fun main() {
    embeddedServer(Netty, port = 8765, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CORS) {
        allowHost("localhost:8080")
        allowHost("127.0.0.1:8080")
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout    = 60.seconds
        maxFrameSize = 64 * 1024 * 1024L // 64 MB
        masking = false
    }

    val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
    val executor        = CliExecutor()
    val emulatorManager = EmulatorManager()
    val fsAgent         = FileSystemAgent()
    val aiProxy         = AiProxy(fsAgent)
    val searcher        = SemanticSearch(aiProxy)
    val scaffolder      = ProjectScaffolder()
    val sessionManager  = SessionManager()
    val agentScope      = CoroutineScope(Dispatchers.IO + SupervisorJob())

    monitor.subscribe(ApplicationStopped) { aiProxy.close(); searcher.close() }

    // Applies diffs → writes to disk → builds → retries with error context (max 3 times)
    suspend fun agentLoop(
        conversationId: String,
        projectRoot: String?,
        prompt: String,
        filePaths: List<String>,
        provider: String,
        apiKey: String,
        customBaseUrl: String?,
        history: List<ChatMessage>,
        router: CommandRouter,
        send: suspend (String) -> Unit
    ) {
        val MAX_ATTEMPTS = 3
        var currentHistory = history
        var currentPrompt  = prompt
        var currentPaths   = filePaths

        for (attempt in 1..MAX_ATTEMPTS) {
            val applyingMsg = if (currentPaths.isEmpty()) "Generating response..." else "Applying changes..."
            send(json.encodeToString(WsMessage.serializer(),
                WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "applying", applyingMsg)))

            val diffs = mutableListOf<WsMessage.DiffSuggestion>()
            var aiText = ""

            aiProxy.streamDiff(
                currentPrompt, currentPaths, provider, apiKey, customBaseUrl,
                conversationId, currentHistory, projectRoot
            ).collect { wsMsg ->
                when (wsMsg) {
                    is WsMessage.DiffSuggestion -> {
                        diffs += wsMsg
                        // autoApplied=true tells the frontend to update Monaco directly (no review dialog)
                        send(json.encodeToString(WsMessage.serializer(), wsMsg.copy(autoApplied = true)))
                    }
                    is WsMessage.AiResponse -> {
                        aiText = wsMsg.text
                        send(json.encodeToString(WsMessage.serializer(), wsMsg))
                    }
                    else -> send(json.encodeToString(WsMessage.serializer(), wsMsg))
                }
            }

            diffs.forEach { diff -> fsAgent.applyDiff(diff.filePath, diff.hunks, projectRoot) }

            if (diffs.isEmpty() || projectRoot == null) {
                // Text-only response or no project root — signal done without a build log entry
                send(json.encodeToString(WsMessage.serializer(),
                    WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "success")))
                return
            }

            send(json.encodeToString(WsMessage.serializer(),
                WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "building")))

            val (buildOk, errors) = router.runProjectBuild(conversationId, projectRoot, send)

            if (buildOk) {
                send(json.encodeToString(WsMessage.serializer(),
                    WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "success", "Build passed")))
                return
            }

            if (attempt == MAX_ATTEMPTS) {
                send(json.encodeToString(WsMessage.serializer(),
                    WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "failed",
                        "Build still failing after $MAX_ATTEMPTS attempts")))
                return
            }

            send(json.encodeToString(WsMessage.serializer(),
                WsMessage.AgentLoopStatus(conversationId, attempt, MAX_ATTEMPTS, "retrying",
                    "Attempt $attempt failed — fixing errors")))

            currentHistory = currentHistory +
                ChatMessage("assistant", aiText) +
                ChatMessage("user", "Build failed. Fix these compilation errors:\n\n$errors")
            currentPrompt = "Fix the compilation errors listed above."
            // Resolve relative paths from AI diffs to absolute so readFilesAsContext can find them.
            // projectRoot is guaranteed non-null here (line 114 returned otherwise).
            currentPaths = diffs.map { diff ->
                val p = diff.filePath
                if (p.startsWith("/")) p else "$projectRoot/$p"
            }
        }
    }

    routing {
        get("/health") { call.respond("ok") }
        get("/ai/test") {
            val provider      = call.request.queryParameters["provider"] ?: "CLAUDE"
            val key           = call.request.queryParameters["key"] ?: ""
            val customBaseUrl = call.request.queryParameters["customBaseUrl"]
            val (ok, error)   = aiProxy.testKey(provider, key, customBaseUrl)
            val result = buildJsonObject {
                put("ok", ok)
                error?.let { put("error", it) }
            }
            call.respondText(result.toString(), ContentType.Application.Json)
        }
        post("/ai/complete") {
            val provider      = call.request.queryParameters["provider"] ?: "CLAUDE"
            val apiKey        = call.request.queryParameters["key"] ?: ""
            val customBaseUrl = call.request.queryParameters["customBaseUrl"]
            val bodyJson      = Json.parseToJsonElement(call.receiveText()).jsonObject
            val prefix        = bodyJson["prefix"]?.jsonPrimitive?.content ?: ""
            val suffix        = bodyJson["suffix"]?.jsonPrimitive?.content ?: ""
            val language      = bodyJson["language"]?.jsonPrimitive?.content ?: "kotlin"
            val completion    = aiProxy.complete(prefix, suffix, language, provider, apiKey, customBaseUrl)
            val result        = buildJsonObject { put("completion", completion) }
            call.respondText(result.toString(), ContentType.Application.Json)
        }
        get("/fs/home") { call.respond(System.getProperty("user.home") ?: "/") }
        get("/fs/list") {
            val path = call.request.queryParameters["path"]
                ?: System.getProperty("user.home") ?: "/"
            call.respond(fsAgent.listDirectory(path))
        }

        // ── Text command channel ─────────────────────────────────────────────
        webSocket("/ws") {
            val router = CommandRouter(executor, emulatorManager, sessionManager, agentScope, json)

            send(json.encodeToString(WsMessage.serializer(), WsMessage.SessionInfo(
                agentVersion = "1.0.0",
                androidCliVersion = runCatching {
                    ProcessBuilder("android", "--version").start()
                        .inputStream.bufferedReader().readLine() ?: "unknown"
                }.getOrDefault("unknown")
            )))

            try { for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> runCatching {
                        when (val msg = json.decodeFromString<WsMessage>(frame.readText())) {
                            is WsMessage.Command -> router.route(msg) { text -> send(text) }

                            is WsMessage.AiRequest -> {
                                if (msg.noPlan) {
                                    // Run in background so the incoming frame loop stays unblocked
                                    // (builds can take minutes; other frames like ListDir must still work)
                                    agentScope.launch {
                                        agentLoop(
                                            msg.conversationId, msg.projectRoot, msg.prompt,
                                            msg.filePaths, msg.provider, msg.apiKey, msg.customBaseUrl,
                                            msg.history, router, send = { send(it) }
                                        )
                                    }
                                } else {
                                    aiProxy.streamPlan(
                                        msg.prompt, msg.filePaths, msg.provider, msg.apiKey, msg.customBaseUrl,
                                        msg.conversationId, msg.history, msg.projectRoot
                                    ).collect { wsMsg ->
                                        send(json.encodeToString(WsMessage.serializer(), wsMsg))
                                    }
                                }
                            }

                            is WsMessage.PlanConfirm -> {
                                val fullPrompt = if (msg.planSummary.isNotBlank())
                                    "Implement this confirmed plan: ${msg.planSummary}\n\nOriginal request: ${msg.prompt}"
                                else msg.prompt
                                val root = msg.projectRoot ?: ""
                                val resolvedPlanPaths = msg.planFilePaths.map { p ->
                                    if (p.startsWith("/")) p else "$root/$p"
                                }
                                val allPaths = (msg.filePaths + resolvedPlanPaths).distinct()
                                agentScope.launch {
                                    agentLoop(
                                        msg.conversationId, msg.projectRoot, fullPrompt,
                                        allPaths, msg.provider, msg.apiKey, msg.customBaseUrl,
                                        msg.history, router, send = { send(it) }
                                    )
                                }
                            }

                            is WsMessage.ListDir -> {
                                val entries = fsAgent.listDirectory(msg.path)
                                send(json.encodeToString(WsMessage.serializer(),
                                    WsMessage.DirListing(msg.path, entries)))
                            }

                            is WsMessage.ReadFile -> {
                                val content = fsAgent.readFile(msg.path)
                                    ?: return@runCatching send(json.encodeToString(
                                        WsMessage.serializer(), WsMessage.ErrorMsg("Cannot read: ${msg.path}")))
                                send(json.encodeToString(WsMessage.serializer(),
                                    WsMessage.FileContent(msg.path, content)))
                            }

                            is WsMessage.WriteFile -> {
                                fsAgent.writeFile(msg.path, msg.content)
                                send(json.encodeToString(WsMessage.serializer(),
                                    WsMessage.Output("write", "", done = true)))
                            }

                            is WsMessage.ScaffoldRequest -> {
                                val scaffoldId = "scaffold:${msg.projectName}"
                                val job = agentScope.launch {
                                    if (!PACKAGE_NAME_REGEX.matches(msg.packageName)) {
                                        send(json.encodeToString(WsMessage.serializer(),
                                            WsMessage.ErrorMsg("Invalid package name: must match Java package convention")))
                                        return@launch
                                    }
                                    val ctx = TemplateContext.from(msg)
                                    val projectDir = File(msg.parentDir, msg.projectName)
                                    val parentCanonical = File(msg.parentDir).canonicalPath
                                    if (!projectDir.canonicalPath.startsWith(parentCanonical + File.separator) &&
                                        projectDir.canonicalPath != parentCanonical) {
                                        send(json.encodeToString(WsMessage.serializer(),
                                            WsMessage.ErrorMsg("Invalid project name: path traversal detected")))
                                        return@launch
                                    }
                                    val createdFresh = !projectDir.exists()
                                    projectDir.mkdirs()
                                    scaffolder.scaffold(ctx, projectDir) { filePath, done, error ->
                                        if (done && error != null && createdFresh) {
                                            projectDir.deleteRecursively()
                                        }
                                        send(json.encodeToString(WsMessage.serializer(),
                                            WsMessage.ScaffoldProgress(filePath, done, error)))
                                    }
                                }
                                sessionManager.register(scaffoldId, job)
                                job.invokeOnCompletion { sessionManager.cancel(scaffoldId) }
                            }

                            is WsMessage.AiScaffoldRequest -> {
                                agentScope.launch {
                                    runCatching {
                                        val config = aiProxy.suggestScaffold(
                                            msg.description, msg.projectName,
                                            msg.provider, msg.apiKey, msg.customBaseUrl
                                        )
                                        send(json.encodeToString(WsMessage.serializer(), config))
                                    }.onFailure { e ->
                                        send(json.encodeToString(WsMessage.serializer(),
                                            WsMessage.ErrorMsg("AI scaffold failed: ${e.message}")))
                                    }
                                }
                            }

                            is WsMessage.ListAvds -> {
                                agentScope.launch {
                                    send(json.encodeToString(WsMessage.serializer(),
                                        emulatorManager.buildAvdList()))
                                }
                            }

                            is WsMessage.SemanticSearchRequest -> {
                                agentScope.launch {
                                    val result = searcher.search(
                                        msg.query, msg.projectRoot,
                                        msg.provider, msg.apiKey, msg.customBaseUrl
                                    )
                                    send(json.encodeToString(WsMessage.serializer(), result))
                                }
                            }

                            else -> {}
                        }
                    }.onFailure { e ->
                        send(json.encodeToString(WsMessage.serializer(), WsMessage.ErrorMsg("Parse error: ${e.message}")))
                    }
                    is Frame.Close -> break
                    else -> {}
                }
            } } finally { sessionManager.cancelAll() }
        }

        // ── Binary video pipe — screen mirror via adb screenrecord ───────────
        webSocket("/ws/video") {
            val device = call.request.queryParameters["device"] ?: return@webSocket
            val process = ProcessBuilder(
                emulatorManager.adbBinPath, "-s", device,
                "exec-out", "screenrecord",
                "--output-format=h264", "--time-limit=3600", "-"
            ).start()

            val streamJob = agentScope.launch(Dispatchers.IO) {
                val buf = ByteArray(65_536)
                process.inputStream.use { stream ->
                    var n = stream.read(buf)
                    while (n != -1 && isActive) {
                        send(Frame.Binary(true, buf.copyOf(n)))
                        n = stream.read(buf)
                    }
                }
            }

            try {
                for (frame in incoming) { if (frame is Frame.Close) break }
            } finally {
                streamJob.cancel()
                process.destroy()
            }
        }
    }
}

package studio.kmp.agent.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import studio.kmp.agent.executor.FileSystemAgent
import studio.kmp.shared.model.*

class AiProxy(private val fsAgent: FileSystemAgent) {

    private val logger = LoggerFactory.getLogger(AiProxy::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        engine { requestTimeout = Limits.REQUEST_TIMEOUT_MS }
    }

    fun close() = client.close()

    // ── Constants ─────────────────────────────────────────────────────────────────

    private object Models {
        const val CLAUDE_SONNET = "claude-sonnet-4-6"
        const val CLAUDE_HAIKU  = "claude-haiku-4-5-20251001"
        const val GPT_4O        = "gpt-4o"
        const val GPT_4O_MINI   = "gpt-4o-mini"
        const val GEMINI_FLASH  = "gemini-1.5-flash"
        const val GEMINI_PRO    = "gemini-1.5-pro"
    }

    private object Limits {
        const val REQUEST_TIMEOUT_MS = 120_000L
        const val STREAM             = 8192
        const val COMPLETE           = 150
        const val SCAFFOLD           = 512
        const val RERANK             = 1024
    }

    private object Urls {
        const val ANTHROPIC  = "https://api.anthropic.com/v1/messages"
        const val OPENAI     = "https://api.openai.com"
        const val GEMINI     = "https://generativelanguage.googleapis.com/v1beta/models"
        fun openAi(base: String?)  = "${(base ?: OPENAI).trimEnd('/')}/v1/chat/completions"
        fun geminiSse(model: String, key: String)      = "$GEMINI/$model:streamGenerateContent?alt=sse&key=$key"
        fun geminiGenerate(model: String, key: String) = "$GEMINI/$model:generateContent?key=$key"
    }

    // ── System prompts ────────────────────────────────────────────────────────────

    private val systemPrompt = """
        You are a Kotlin expert assistant embedded in KMP Studio.
        When suggesting code changes, use one DIFF_START/DIFF_END block PER FILE.
        You can and should modify as many files as needed in a single response.

        DIFF_START
        FILE: <relative/path/to/file.kt>
        @@ -<startLine>,<count> +<startLine>,<count> @@
        -<removed line>
        +<added line>
         <context line>
        DIFF_END

        Rules:
        - Each modified file gets its own DIFF_START/DIFF_END block.
        - FILE: path must be the relative path from the project root.
        - startLine is 1-indexed.
        - Never include explanations outside DIFF_START/DIFF_END unless explicitly asked.
    """.trimIndent()

    private val planSystemPrompt = """
        You are a Kotlin expert assistant embedded in KMP Studio.
        When asked to make code changes, respond ONLY with a structured change plan.

        Use this exact format:
        PLAN_START
        SUMMARY: <one-line description of what will be done>
        FILE: <relative/path/to/file.kt> | <one-line description of what changes in this file>
        FILE: <relative/path/to/file.kt> | <one-line description of what changes in this file>
        PLAN_END

        Do not generate any code. Output only the PLAN_START...PLAN_END block.
    """.trimIndent()

    // ── Public API ────────────────────────────────────────────────────────────────

    fun streamDiff(
        prompt: String,
        filePaths: List<String>,
        provider: String = "CLAUDE",
        apiKey: String = "",
        customBaseUrl: String? = null,
        conversationId: String = "",
        history: List<ChatMessage> = emptyList(),
        projectRoot: String? = null
    ): Flow<WsMessage> = flow {
        val resolvedKey = resolveKey(provider, apiKey) ?: return@flow

        val userContent = buildUserContent(filePaths, projectRoot, prompt)
        val accumulated = streamProvider(provider, resolvedKey, customBaseUrl, userContent, history)

        DiffParser.parseDiffs(accumulated).forEach { emit(it) }
        emit(WsMessage.AiResponse(conversationId, accumulated))
    }

    fun streamPlan(
        prompt: String,
        filePaths: List<String>,
        provider: String = "CLAUDE",
        apiKey: String = "",
        customBaseUrl: String? = null,
        conversationId: String = "",
        history: List<ChatMessage> = emptyList(),
        projectRoot: String? = null
    ): Flow<WsMessage> = flow {
        val resolvedKey = resolveKey(provider, apiKey) ?: return@flow

        val userContent  = buildUserContent(filePaths, projectRoot, prompt)
        val accumulated  = streamProvider(provider, resolvedKey, customBaseUrl, userContent, history, planSystemPrompt)
        val plan         = DiffParser.parsePlan(accumulated, conversationId)

        if (plan != null) emit(plan)
        else emit(WsMessage.ErrorMsg("AI did not return a valid plan. Try rephrasing your request."))
    }

    suspend fun complete(
        prefix: String,
        suffix: String,
        language: String,
        provider: String = "CLAUDE",
        apiKey: String = "",
        customBaseUrl: String? = null
    ): String {
        val resolvedKey = resolveKey(provider, apiKey) ?: return ""
        val sysPrompt = "You are a $language code completion engine. " +
            "Given code with a <CURSOR> marker, output ONLY the text that belongs at the cursor. " +
            "Do not repeat code before the cursor. Keep it concise (1-3 lines). No markdown, no backticks."
        return callNonStreaming(
            provider, resolvedKey, customBaseUrl,
            anthropicModel  = Models.CLAUDE_HAIKU,
            openAiModel     = Models.GPT_4O_MINI,
            geminiModel     = Models.GEMINI_FLASH,
            maxTokens       = Limits.COMPLETE,
            sysPrompt       = sysPrompt,
            userContent     = "$prefix<CURSOR>$suffix",
            history         = emptyList()
        )
    }

    suspend fun suggestScaffold(
        description: String,
        projectNameHint: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): WsMessage.AiScaffoldConfig {
        val resolvedKey = resolveKey(provider, apiKey) ?: return defaultScaffoldConfig(projectNameHint)
        val sysPrompt = """
            You are a Kotlin Multiplatform expert. Given an app description, return a JSON scaffold configuration.

            Available targets: android, ios, desktop, web
            Available architectures: clean, mvvm, mvi, library
            Available optional libraries: ktor, sqldelight, datastore, koin, coil, voyager, molecule
            Core libraries (always included, do NOT list): coroutines, serialization, datetime, settings

            Respond ONLY with valid JSON, no markdown fences, no extra text:
            {"projectName":"AppName","packageName":"com.example.appname","targets":["android","ios"],"architecture":"clean","libraries":["ktor","koin"],"reasoning":"one sentence"}

            Rules:
            - projectName: PascalCase, no spaces (use hint if provided)
            - packageName: all-lowercase, reverse-domain style
            - targets: mobile → android+ios; cross-platform → add desktop; web presence → add web
            - architecture: library for SDKs/libraries published as Maven artifacts; clean for complex apps; mvvm for simple CRUD; mvi for reactive/event-driven
            - library architecture: include only targets that consumers will actually need (e.g. android+ios for a mobile SDK, add desktop/web only if explicitly requested); never include coil/voyager/molecule (UI-only)
            - ktor: remote API calls; sqldelight: local DB; datastore: prefs/settings; koin: DI for medium+ apps
            - coil: network images; voyager: multi-screen navigation; molecule: complex reactive state
        """.trimIndent()
        val userContent = buildString {
            if (projectNameHint.isNotBlank()) appendLine("Project name hint: $projectNameHint")
            append("App description: $description")
        }
        val raw = callNonStreaming(
            provider, resolvedKey, customBaseUrl,
            anthropicModel = Models.CLAUDE_SONNET,
            openAiModel    = Models.GPT_4O,
            geminiModel    = Models.GEMINI_PRO,
            maxTokens      = Limits.SCAFFOLD,
            sysPrompt      = sysPrompt,
            userContent    = userContent,
            history        = emptyList()
        )
        return parseScaffoldConfig(raw, projectNameHint) ?: defaultScaffoldConfig(projectNameHint)
    }

    suspend fun rerankResults(
        query: String,
        candidates: List<SearchHit>,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): List<SearchHit> {
        val resolvedKey = resolveKey(provider, apiKey) ?: return candidates.take(5)
        val sysPrompt = """
            You are a code search assistant. Given a natural language query and candidate code snippets,
            identify the top 5 most relevant results and add a brief explanation for each.

            Respond ONLY with a valid JSON array — no markdown, no extra text:
            [{"filePath":"...","lineNumber":N,"snippet":"...","explanation":"..."},...]

            Order results by relevance (most relevant first). Return at most 5 items.
        """.trimIndent()
        val candidatesText = candidates.mapIndexed { i, hit ->
            "${i + 1}. ${hit.filePath}:${hit.lineNumber}\n   ${hit.snippet}"
        }.joinToString("\n")
        val raw = callNonStreaming(
            provider, resolvedKey, customBaseUrl,
            anthropicModel = Models.CLAUDE_HAIKU,
            openAiModel    = Models.GPT_4O_MINI,
            geminiModel    = Models.GEMINI_FLASH,
            maxTokens      = Limits.RERANK,
            sysPrompt      = sysPrompt,
            userContent    = "Query: $query\n\nCandidates:\n$candidatesText",
            history        = emptyList()
        )
        return parseRerankJson(raw) ?: candidates.take(5)
    }

    suspend fun testKey(provider: String, apiKey: String, customBaseUrl: String?): Pair<Boolean, String?> {
        if (apiKey.isBlank() && provider != "CUSTOM") return Pair(false, "No API key provided")
        return try {
            val response = when (provider) {
                "OPENAI" -> testOpenAi(apiKey, Urls.openAi(null))
                "CUSTOM" -> testOpenAi(apiKey, Urls.openAi(customBaseUrl))
                "GEMINI" -> testGemini(apiKey)
                else     -> testAnthropic(apiKey)
            }
            if (response.status.value in 200..299) Pair(true, null)
            else Pair(false, extractErrorMessage(response) ?: "HTTP ${response.status.value}")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Connection failed")
        }
    }

    // ── Streaming providers ───────────────────────────────────────────────────────

    private suspend fun streamProvider(
        provider: String,
        apiKey: String,
        customBaseUrl: String?,
        userContent: String,
        history: List<ChatMessage>,
        sysPrompt: String = systemPrompt
    ): String = when (provider) {
        "OPENAI" -> streamOpenAi(apiKey, Urls.openAi(null), userContent, history, sysPrompt)
        "CUSTOM" -> streamOpenAi(apiKey, Urls.openAi(customBaseUrl), userContent, history, sysPrompt)
        "GEMINI" -> streamGemini(apiKey, userContent, history, sysPrompt)
        else     -> streamAnthropic(apiKey, userContent, history, sysPrompt)
    }

    private suspend fun streamAnthropic(
        apiKey: String,
        userContent: String,
        history: List<ChatMessage>,
        sysPrompt: String = systemPrompt
    ): String {
        val body = buildJsonObject {
            put("model", Models.CLAUDE_SONNET)
            put("max_tokens", Limits.STREAM)
            put("stream", true)
            putJsonArray("system") {
                addJsonObject {
                    put("type", "text"); put("text", sysPrompt)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                }
            }
            putJsonArray("messages") {
                history.forEach { msg ->
                    addJsonObject { put("role", msg.role); put("content", msg.content) }
                }
                addJsonObject { put("role", "user"); put("content", userContent) }
            }
        }
        val response = client.post(Urls.ANTHROPIC) {
            contentType(ContentType.Application.Json)
            anthropicHeaders(apiKey).forEach { (k, v) -> header(k, v) }
            header("anthropic-beta", "prompt-caching-2024-07-31")
            setBody(body.toString())
        }
        return collectSse(response) { data ->
            runCatching {
                Json.parseToJsonElement(data).jsonObject["delta"]
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content
            }.getOrNull()
        }
    }

    private suspend fun streamOpenAi(
        apiKey: String,
        url: String,
        userContent: String,
        history: List<ChatMessage>,
        sysPrompt: String = systemPrompt
    ): String {
        val body = buildJsonObject {
            put("model", Models.GPT_4O)
            put("stream", true)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", sysPrompt) }
                history.forEach { msg ->
                    addJsonObject { put("role", msg.role); put("content", msg.content) }
                }
                addJsonObject { put("role", "user"); put("content", userContent) }
            }
        }
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            openAiHeaders(apiKey).forEach { (k, v) -> header(k, v) }
            setBody(body.toString())
        }
        return collectSse(response) { data ->
            runCatching {
                Json.parseToJsonElement(data).jsonObject["choices"]
                    ?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("delta")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
            }.getOrNull()
        }
    }

    private suspend fun streamGemini(
        apiKey: String,
        userContent: String,
        history: List<ChatMessage>,
        sysPrompt: String = systemPrompt
    ): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", sysPrompt) } }
            }
            putJsonArray("contents") {
                history.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        putJsonArray("parts") { addJsonObject { put("text", msg.content) } }
                    }
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", userContent) } }
                }
            }
        }
        val response = client.post(Urls.geminiSse(Models.GEMINI_FLASH, apiKey)) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return collectSse(response) { data ->
            runCatching {
                Json.parseToJsonElement(data).jsonObject["candidates"]
                    ?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
            }.getOrNull()
        }
    }

    // ── SSE collector ─────────────────────────────────────────────────────────────

    private suspend fun collectSse(response: HttpResponse, extract: (String) -> String?): String = buildString {
        val channel    = response.bodyAsChannel()
        val lineBuffer = StringBuilder()
        while (!channel.isClosedForRead) {
            val byte = channel.readByte().toInt().toChar()
            if (byte == '\n') {
                val line = lineBuffer.toString().trim()
                lineBuffer.clear()
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    extract(data)?.let { append(it) }
                }
            } else {
                lineBuffer.append(byte)
            }
        }
    }

    // ── Non-streaming generic dispatcher ─────────────────────────────────────────

    private suspend fun callNonStreaming(
        provider: String,
        apiKey: String,
        customBaseUrl: String?,
        anthropicModel: String,
        openAiModel: String,
        geminiModel: String,
        maxTokens: Int,
        sysPrompt: String,
        userContent: String,
        history: List<ChatMessage>
    ): String = try {
        when (provider) {
            "OPENAI" -> {
                val body = openAiBody(openAiModel, maxTokens, sysPrompt, history, userContent)
                parseOpenAiResponse(nonStreamingPost(Urls.openAi(null), body, openAiHeaders(apiKey)))
            }
            "CUSTOM" -> {
                val body = openAiBody(openAiModel, maxTokens, sysPrompt, history, userContent)
                parseOpenAiResponse(nonStreamingPost(Urls.openAi(customBaseUrl), body, openAiHeaders(apiKey)))
            }
            "GEMINI" -> {
                val body = geminiBody(sysPrompt, history, userContent, maxTokens)
                parseGeminiResponse(nonStreamingPost(Urls.geminiGenerate(geminiModel, apiKey), body))
            }
            else -> {
                val body = anthropicBody(anthropicModel, maxTokens, sysPrompt, history, userContent)
                parseAnthropicResponse(nonStreamingPost(Urls.ANTHROPIC, body, anthropicHeaders(apiKey)))
            }
        }
    } catch (e: Exception) {
        logger.error("Non-streaming AI call failed [provider=$provider]", e)
        throw e
    }

    // ── Request body builders ─────────────────────────────────────────────────────

    private fun anthropicBody(
        model: String, maxTokens: Int, sysPrompt: String,
        history: List<ChatMessage>, userContent: String
    ) = buildJsonObject {
        put("model", model); put("max_tokens", maxTokens)
        putJsonArray("system") {
            addJsonObject { put("type", "text"); put("text", sysPrompt) }
        }
        putJsonArray("messages") {
            history.forEach { msg ->
                addJsonObject { put("role", msg.role); put("content", msg.content) }
            }
            addJsonObject { put("role", "user"); put("content", userContent) }
        }
    }

    private fun openAiBody(
        model: String, maxTokens: Int, sysPrompt: String,
        history: List<ChatMessage>, userContent: String
    ) = buildJsonObject {
        put("model", model); put("max_tokens", maxTokens)
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", sysPrompt) }
            history.forEach { msg ->
                addJsonObject { put("role", msg.role); put("content", msg.content) }
            }
            addJsonObject { put("role", "user"); put("content", userContent) }
        }
    }

    private fun geminiBody(
        sysPrompt: String, history: List<ChatMessage>, userContent: String, maxTokens: Int
    ) = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", sysPrompt) } }
        }
        putJsonArray("contents") {
            history.forEach { msg ->
                addJsonObject {
                    put("role", if (msg.role == "assistant") "model" else "user")
                    putJsonArray("parts") { addJsonObject { put("text", msg.content) } }
                }
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", userContent) } }
            }
        }
        putJsonObject("generationConfig") { put("maxOutputTokens", maxTokens) }
    }

    // ── Response parsers ──────────────────────────────────────────────────────────

    private fun parseAnthropicResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["content"]
            ?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
    }.getOrDefault("")

    private fun parseOpenAiResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["choices"]
            ?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content ?: ""
    }.getOrDefault("")

    private fun parseGeminiResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["candidates"]
            ?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content ?: ""
    }.getOrDefault("")

    // ── HTTP helpers ──────────────────────────────────────────────────────────────

    private suspend fun nonStreamingPost(url: String, body: JsonObject, headers: Map<String, String> = emptyMap()): String =
        client.post(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (k, v) -> header(k, v) }
            setBody(body.toString())
        }.bodyAsText()

    private fun anthropicHeaders(apiKey: String) = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    private fun openAiHeaders(apiKey: String): Map<String, String> =
        if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()

    // ── Key test helpers ──────────────────────────────────────────────────────────

    private suspend fun testAnthropic(apiKey: String): HttpResponse =
        client.post(Urls.ANTHROPIC) {
            contentType(ContentType.Application.Json)
            anthropicHeaders(apiKey).forEach { (k, v) -> header(k, v) }
            setBody("""{"model":"${Models.CLAUDE_HAIKU}","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}""")
        }

    private suspend fun testOpenAi(apiKey: String, url: String): HttpResponse =
        client.post(url) {
            contentType(ContentType.Application.Json)
            openAiHeaders(apiKey).forEach { (k, v) -> header(k, v) }
            setBody("""{"model":"${Models.GPT_4O_MINI}","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}""")
        }

    private suspend fun testGemini(apiKey: String): HttpResponse =
        client.post(Urls.geminiGenerate(Models.GEMINI_FLASH, apiKey)) {
            contentType(ContentType.Application.Json)
            setBody("""{"contents":[{"parts":[{"text":"hi"}]}]}""")
        }

    private suspend fun extractErrorMessage(response: HttpResponse): String? = runCatching {
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            ?: json["error"]?.jsonPrimitive?.content
    }.getOrNull()

    // ── Scaffold & rerank parsers ─────────────────────────────────────────────────

    private fun parseScaffoldConfig(raw: String, nameHint: String): WsMessage.AiScaffoldConfig? = runCatching {
        val jsonStr = DiffParser.extractFirstJsonObject(raw) ?: return@runCatching null
        val obj     = Json.parseToJsonElement(jsonStr).jsonObject
        WsMessage.AiScaffoldConfig(
            projectName  = obj["projectName"]?.jsonPrimitive?.content?.ifBlank { null }
                            ?: nameHint.ifBlank { "MyKmpApp" },
            packageName  = obj["packageName"]?.jsonPrimitive?.content ?: scaffoldDerivePackage(nameHint),
            targets      = obj["targets"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("android"),
            architecture = obj["architecture"]?.jsonPrimitive?.content ?: "clean",
            libraries    = obj["libraries"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            reasoning    = obj["reasoning"]?.jsonPrimitive?.content ?: ""
        )
    }.getOrNull()

    private fun parseRerankJson(raw: String): List<SearchHit>? = runCatching {
        val jsonStr = DiffParser.extractFirstJsonArray(raw) ?: return@runCatching null
        Json.parseToJsonElement(jsonStr).jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            SearchHit(
                filePath    = obj["filePath"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                lineNumber  = obj["lineNumber"]?.jsonPrimitive?.int ?: 1,
                snippet     = obj["snippet"]?.jsonPrimitive?.content ?: "",
                explanation = obj["explanation"]?.jsonPrimitive?.content ?: ""
            )
        }
    }.getOrNull()

    private fun defaultScaffoldConfig(nameHint: String) = WsMessage.AiScaffoldConfig(
        projectName  = nameHint.ifBlank { "MyKmpApp" },
        packageName  = scaffoldDerivePackage(nameHint),
        targets      = listOf("android", "ios"),
        architecture = "clean",
        libraries    = listOf("ktor", "koin"),
        reasoning    = "Default configuration — AI response could not be parsed"
    )

    private fun scaffoldDerivePackage(name: String): String {
        val safe = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        return if (safe.isBlank()) "com.example.app" else "com.example.$safe"
    }

    // ── Context builder ───────────────────────────────────────────────────────────

    private fun buildUserContent(filePaths: List<String>, projectRoot: String?, prompt: String): String {
        val autoContext = fsAgent.gatherAutoContext(filePaths.firstOrNull(), projectRoot)
        val fileContext = fsAgent.readFilesAsContext(filePaths)
        return buildString {
            if (autoContext.isNotBlank()) { appendLine(autoContext); appendLine() }
            if (fileContext.isNotBlank()) { appendLine("## Active File"); appendLine(fileContext); appendLine() }
            append(prompt)
        }
    }

    private fun resolveKey(provider: String, apiKey: String): String? {
        val key = apiKey.ifBlank {
            if (provider == "CLAUDE") System.getenv("ANTHROPIC_API_KEY") ?: "" else ""
        }
        return if (key.isBlank() && provider != "CUSTOM") null else key
    }
}

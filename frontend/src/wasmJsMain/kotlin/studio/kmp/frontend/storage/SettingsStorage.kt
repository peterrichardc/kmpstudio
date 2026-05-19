package studio.kmp.frontend.storage

import kotlinx.browser.localStorage
import kotlinx.serialization.json.*
import studio.kmp.frontend.ui.ProjectState

object SettingsStorage {
    private const val K_CLAUDE  = "kmp_claude_key"
    private const val K_OPENAI  = "kmp_openai_key"
    private const val K_GEMINI  = "kmp_gemini_key"
    private const val K_CUSTOM_URL = "kmp_custom_url"
    private const val K_CUSTOM_KEY = "kmp_custom_key"
    private const val K_PROVIDER = "kmp_ai_provider"
    private const val K_PORT     = "kmp_agent_port"

    var claudeApiKey: String
        get() = localStorage.getItem(K_CLAUDE) ?: ""
        set(v) { localStorage.setItem(K_CLAUDE, v) }

    var openAiApiKey: String
        get() = localStorage.getItem(K_OPENAI) ?: ""
        set(v) { localStorage.setItem(K_OPENAI, v) }

    var geminiApiKey: String
        get() = localStorage.getItem(K_GEMINI) ?: ""
        set(v) { localStorage.setItem(K_GEMINI, v) }

    var customBaseUrl: String
        get() = localStorage.getItem(K_CUSTOM_URL) ?: ""
        set(v) { localStorage.setItem(K_CUSTOM_URL, v) }

    var customApiKey: String
        get() = localStorage.getItem(K_CUSTOM_KEY) ?: ""
        set(v) { localStorage.setItem(K_CUSTOM_KEY, v) }

    var aiProvider: AiProvider
        get() = AiProvider.entries.find { it.name == localStorage.getItem(K_PROVIDER) } ?: AiProvider.CLAUDE
        set(v) { localStorage.setItem(K_PROVIDER, v.name) }

    var agentPort: Int
        get() = localStorage.getItem(K_PORT)?.toIntOrNull() ?: 8765
        set(v) { localStorage.setItem(K_PORT, v.toString()) }

    private const val K_RECENTS  = "kmp_recent_projects"

    var recentProjects: List<ProjectState>
        get() = runCatching {
            Json.parseToJsonElement(localStorage.getItem(K_RECENTS) ?: "[]")
                .jsonArray
                .map {
                    ProjectState(
                        name = it.jsonObject["name"]!!.jsonPrimitive.content,
                        path = it.jsonObject["path"]!!.jsonPrimitive.content
                    )
                }
        }.getOrDefault(emptyList())
        set(v) {
            val json = buildJsonArray {
                v.forEach { p ->
                    addJsonObject {
                        put("name", p.name)
                        put("path", p.path)
                    }
                }
            }
            localStorage.setItem(K_RECENTS, json.toString())
        }

    fun addRecentProject(project: ProjectState) {
        recentProjects = (listOf(project) + recentProjects)
            .distinctBy { it.path }
            .take(10)
    }

    fun activeApiKey(): String = when (aiProvider) {
        AiProvider.CLAUDE  -> claudeApiKey
        AiProvider.OPENAI  -> openAiApiKey
        AiProvider.GEMINI  -> geminiApiKey
        AiProvider.CUSTOM  -> customApiKey
    }
}

enum class AiProvider(val displayName: String) {
    CLAUDE("Claude (Anthropic)"),
    OPENAI("OpenAI GPT-4o"),
    GEMINI("Google Gemini"),
    CUSTOM("Custom OpenAI-compatible")
}

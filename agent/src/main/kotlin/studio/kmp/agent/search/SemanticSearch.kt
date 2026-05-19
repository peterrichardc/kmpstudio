package studio.kmp.agent.search

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import studio.kmp.agent.ai.AiProxy
import studio.kmp.shared.model.SearchHit
import studio.kmp.shared.model.WsMessage
import java.io.File

class SemanticSearch(private val aiProxy: AiProxy) {

    private val includedExtensions = setOf(
        "kt", "kts", "xml", "toml", "gradle", "json", "yaml", "yml", "properties", "swift", "m"
    )
    private val excludedDirs = setOf(
        "build", ".gradle", ".idea", "node_modules", ".git", ".cxx", "intermediates"
    )

    // Providers that expose an OpenAI-compatible /v1/embeddings endpoint
    private val embeddingProviders = setOf("OPENAI", "CUSTOM")

    private val httpClient = HttpClient(CIO)

    // Simple in-memory cache: rebuild when project files are newer than the last index build
    private var cachedIndex: EmbeddingIndex? = null
    private var cachedRoot: String = ""

    suspend fun search(
        query: String,
        projectRoot: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): WsMessage.SemanticSearchResult {
        val candidates = when {
            provider in embeddingProviders ->
                vectorSearch(query, projectRoot, provider, apiKey, customBaseUrl)
            else ->
                grepProject(query, projectRoot)
        }
        val hits = if (candidates.isEmpty()) emptyList()
                   else aiProxy.rerankResults(query, candidates, provider, apiKey, customBaseUrl)
        return WsMessage.SemanticSearchResult(query, hits)
    }

    fun close() = httpClient.close()

    // ── Vector search path ────────────────────────────────────────────────────────

    private suspend fun vectorSearch(
        query: String,
        projectRoot: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): List<SearchHit> {
        val index = indexFor(projectRoot, provider, apiKey, customBaseUrl)
            ?: return grepProject(query, projectRoot)

        val queryVec = index.embedQuery(query, provider, apiKey, customBaseUrl)
            ?: return grepProject(query, projectRoot)

        return index.query(queryVec, topK = 40)
    }

    /** Returns a valid (possibly cached) index, or null if building failed. */
    private suspend fun indexFor(
        projectRoot: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): EmbeddingIndex? {
        val currentMtime = maxMtimeOf(projectRoot)
        val existing = cachedIndex
        if (existing != null && cachedRoot == projectRoot && existing.lastIndexedMtime >= currentMtime) {
            return existing
        }
        val index = EmbeddingIndex(httpClient)
        index.build(projectRoot, provider, apiKey, customBaseUrl, includedExtensions, excludedDirs)
        return if (index.isEmpty()) null
        else { cachedIndex = index; cachedRoot = projectRoot; index }
    }

    private fun maxMtimeOf(projectRoot: String): Long {
        val root = File(projectRoot)
        if (!root.isDirectory) return 0L
        return root.walkTopDown()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { it.isFile && it.extension in includedExtensions }
            .maxOfOrNull { it.lastModified() } ?: 0L
    }

    // ── Grep fallback path (Claude / Gemini) ──────────────────────────────────────

    internal fun grepProject(query: String, projectRoot: String): List<SearchHit> {
        val root = File(projectRoot)
        if (!root.isDirectory) return emptyList()

        val tokens = query.trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .map { it.lowercase() }
        if (tokens.isEmpty()) return emptyList()

        val hits = mutableListOf<SearchHit>()
        root.walkTopDown()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { it.isFile && it.extension in includedExtensions }
            .forEach { file ->
                runCatching {
                    val relativePath = file.relativeTo(root).path
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            val lower = line.lowercase()
                            if (tokens.any { token -> lower.contains(token) }) {
                                hits.add(SearchHit(
                                    filePath   = relativePath,
                                    lineNumber = idx + 1,
                                    snippet    = line.trim().take(200)
                                ))
                            }
                        }
                    }
                }
            }

        return hits.take(40)
    }
}

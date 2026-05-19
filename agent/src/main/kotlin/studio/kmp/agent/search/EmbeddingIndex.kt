package studio.kmp.agent.search

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import studio.kmp.shared.model.SearchHit
import java.io.File
import kotlin.math.sqrt

/**
 * In-memory vector index for semantic code search.
 *
 * Build the index once per project root, then query with an embedded search string.
 * Both build() and embedQuery() call the OpenAI-compatible /v1/embeddings endpoint;
 * callers that don't support embeddings should use the grep fallback in SemanticSearch instead.
 *
 * Cache invalidation: [lastIndexedMtime] holds the max file mtime at index-build time.
 * Callers can compare against current mtime to decide when to rebuild.
 */
class EmbeddingIndex(private val httpClient: HttpClient) {

    data class Chunk(
        val filePath: String,
        val lineNumber: Int,
        val snippet: String,
        val embedding: FloatArray
    )

    private val chunks = mutableListOf<Chunk>()
    var lastIndexedMtime: Long = 0L
        private set

    // ── Public API ────────────────────────────────────────────────────────────────

    suspend fun build(
        projectRoot: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?,
        includedExtensions: Set<String>,
        excludedDirs: Set<String>
    ) {
        chunks.clear()
        val root = File(projectRoot)
        val rawChunks = mutableListOf<Triple<String, Int, String>>() // (relativePath, line, text)
        var maxMtime = 0L

        root.walkTopDown()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { it.isFile && it.extension in includedExtensions }
            .forEach { file ->
                maxMtime = maxOf(maxMtime, file.lastModified())
                val relative = file.relativeTo(root).path
                extractChunks(file, relative).forEach { (line, text) ->
                    rawChunks += Triple(relative, line, text)
                }
            }

        // Batch-embed: 20 chunks per request keeps token count manageable
        rawChunks.chunked(20).forEach { batch ->
            val embeddings = embed(batch.map { it.third }, provider, apiKey, customBaseUrl)
                ?: return@forEach
            batch.forEachIndexed { i, (path, line, text) ->
                if (i < embeddings.size) {
                    chunks += Chunk(path, line, text.take(200), embeddings[i])
                }
            }
        }

        lastIndexedMtime = maxMtime
    }

    /** Returns top-k hits ranked by cosine similarity to [queryEmbedding]. */
    fun query(queryEmbedding: FloatArray, topK: Int = 20): List<SearchHit> =
        chunks
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { (_, score) -> score }
            .take(topK)
            .map { (chunk, _) ->
                SearchHit(filePath = chunk.filePath, lineNumber = chunk.lineNumber, snippet = chunk.snippet)
            }

    suspend fun embedQuery(
        query: String,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): FloatArray? = embed(listOf(query), provider, apiKey, customBaseUrl)?.firstOrNull()

    fun isEmpty(): Boolean = chunks.isEmpty()

    // ── Chunking ──────────────────────────────────────────────────────────────────

    /**
     * Splits [file] into semantically meaningful chunks.
     *
     * Chunks break on top-level Kotlin declarations (fun/class/object/interface) once
     * the current chunk has at least [MIN_CHUNK_LINES] lines, and hard-cap at [MAX_CHUNK_LINES].
     * Returns a list of (1-indexed startLine, chunkText) pairs.
     */
    internal fun extractChunks(file: File, @Suppress("UNUSED_PARAMETER") relativePath: String): List<Pair<Int, String>> {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val result = mutableListOf<Pair<Int, String>>()
        var chunkStart = 0
        val buffer = mutableListOf<String>()

        lines.forEachIndexed { idx, line ->
            val isDeclaration = DECLARATION_RE.containsMatchIn(line)
            if (isDeclaration && buffer.size >= MIN_CHUNK_LINES) {
                result += (chunkStart + 1) to buffer.joinToString("\n")
                buffer.clear()
                chunkStart = idx
            }
            buffer += line
            if (buffer.size >= MAX_CHUNK_LINES) {
                result += (chunkStart + 1) to buffer.joinToString("\n")
                buffer.clear()
                chunkStart = idx + 1
            }
        }
        if (buffer.isNotEmpty()) result += (chunkStart + 1) to buffer.joinToString("\n")
        return result
    }

    // ── Embeddings API ────────────────────────────────────────────────────────────

    private suspend fun embed(
        texts: List<String>,
        provider: String,
        apiKey: String,
        customBaseUrl: String?
    ): List<FloatArray>? {
        val url = when (provider) {
            "CUSTOM" -> "${(customBaseUrl ?: "http://localhost:11434").trimEnd('/')}/v1/embeddings"
            else     -> "https://api.openai.com/v1/embeddings"
        }
        return runCatching {
            val body = buildJsonObject {
                put("model", EMBEDDING_MODEL)
                putJsonArray("input") { texts.forEach { add(it) } }
            }
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
                setBody(body.toString())
            }
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]
                ?.jsonArray
                ?.mapNotNull { item ->
                    item.jsonObject["embedding"]?.jsonArray
                        ?.map { it.jsonPrimitive.float }
                        ?.toFloatArray()
                }
        }.getOrNull()
    }

    // ── Math ──────────────────────────────────────────────────────────────────────

    internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    // ── Constants ─────────────────────────────────────────────────────────────────

    private companion object {
        const val EMBEDDING_MODEL = "text-embedding-3-small"
        const val MIN_CHUNK_LINES = 5
        const val MAX_CHUNK_LINES = 40
        val DECLARATION_RE = Regex(
            """^\s*(fun |class |object |interface |data class |sealed class |abstract class |enum class |typealias )"""
        )
    }
}

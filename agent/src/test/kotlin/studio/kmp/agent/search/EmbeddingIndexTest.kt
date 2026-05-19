package studio.kmp.agent.search

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class EmbeddingIndexTest {

    // ── cosineSimilarity ──────────────────────────────────────────────────────────

    @Test
    fun `cosineSimilarity of identical vectors is 1`() {
        val index = testIndex()
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, index.cosineSimilarity(v, v), absoluteTolerance = 1e-6f)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors is 0`() {
        val index = testIndex()
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, index.cosineSimilarity(a, b), absoluteTolerance = 1e-6f)
    }

    @Test
    fun `cosineSimilarity of opposite vectors is -1`() {
        val index = testIndex()
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, index.cosineSimilarity(a, b), absoluteTolerance = 1e-6f)
    }

    @Test
    fun `cosineSimilarity returns 0 for zero vectors`() {
        val index = testIndex()
        val zero = floatArrayOf(0f, 0f, 0f)
        val v    = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, index.cosineSimilarity(zero, v))
    }

    @Test
    fun `cosineSimilarity returns 0 for mismatched sizes`() {
        val index = testIndex()
        assertEquals(0f, index.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f)))
    }

    @Test
    fun `cosineSimilarity is commutative`() {
        val index = testIndex()
        val a = floatArrayOf(1f, 3f, -2f)
        val b = floatArrayOf(4f, -1f, 0f)
        val ab = index.cosineSimilarity(a, b)
        val ba = index.cosineSimilarity(b, a)
        assertEquals(ab, ba, absoluteTolerance = 1e-6f)
    }

    // ── extractChunks ─────────────────────────────────────────────────────────────

    @Test
    fun `extractChunks returns one chunk for short file`() {
        val file = tempKtFile("""
            package test

            val x = 1
        """.trimIndent())
        val chunks = testIndex().extractChunks(file, "test.kt")
        assertEquals(1, chunks.size)
    }

    @Test
    fun `extractChunks splits on fun declarations`() {
        val code = buildString {
            repeat(6) { appendLine("// line $it") }
            appendLine("fun alpha() {}")
            repeat(6) { appendLine("// line $it") }
            appendLine("fun beta() {}")
        }
        val file = tempKtFile(code)
        val chunks = testIndex().extractChunks(file, "test.kt")
        assertTrue(chunks.size >= 2, "Expected at least 2 chunks, got ${chunks.size}")
    }

    @Test
    fun `extractChunks hard caps at 40 lines`() {
        val code = (1..90).joinToString("\n") { "val v$it = $it" }
        val file = tempKtFile(code)
        val chunks = testIndex().extractChunks(file, "test.kt")
        assertTrue(chunks.size >= 2, "90 lines should produce at least 2 chunks")
        chunks.forEach { (_, text) ->
            assertTrue(text.lines().size <= 40, "No chunk should exceed 40 lines")
        }
    }

    @Test
    fun `extractChunks returns correct 1-indexed start line`() {
        val file = tempKtFile("line1\nline2\nline3")
        val chunks = testIndex().extractChunks(file, "test.kt")
        assertEquals(1, chunks.first().first, "Start line should be 1-indexed")
    }

    @Test
    fun `extractChunks handles empty file`() {
        val file = tempKtFile("")
        assertTrue(testIndex().extractChunks(file, "empty.kt").isEmpty())
    }

    // ── query ranking ─────────────────────────────────────────────────────────────

    @Test
    fun `query returns hits ordered by cosine similarity`() {
        val index = populatedIndex(listOf(
            Triple("a.kt", 1, floatArrayOf(1f, 0f)),
            Triple("b.kt", 1, floatArrayOf(0f, 1f)),
            Triple("c.kt", 1, floatArrayOf(0.9f, 0.1f))
        ))
        val query = floatArrayOf(1f, 0f)
        val hits = index.query(query, topK = 3)
        assertEquals("a.kt", hits[0].filePath)
        assertEquals("c.kt", hits[1].filePath)
        assertEquals("b.kt", hits[2].filePath)
    }

    @Test
    fun `query respects topK limit`() {
        val index = populatedIndex((1..10).map { Triple("f$it.kt", 1, floatArrayOf(it.toFloat(), 0f)) })
        val hits = index.query(floatArrayOf(1f, 0f), topK = 3)
        assertEquals(3, hits.size)
    }

    @Test
    fun `isEmpty returns true before build`() {
        assertTrue(testIndex().isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun testIndex() = EmbeddingIndex(HttpClient(MockEngine) { engine { addHandler { respondOk("") } } })

    private fun tempKtFile(content: String): File {
        val f = Files.createTempFile("kmp-test-", ".kt").toFile()
        f.writeText(content)
        f.deleteOnExit()
        return f
    }

    /** Creates an EmbeddingIndex pre-populated with chunks at given embeddings (bypasses HTTP). */
    private fun populatedIndex(entries: List<Triple<String, Int, FloatArray>>): EmbeddingIndex {
        val index = testIndex()
        val chunksField = EmbeddingIndex::class.java.getDeclaredField("chunks")
        chunksField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val chunks = chunksField.get(index) as MutableList<EmbeddingIndex.Chunk>
        entries.forEach { (path, line, vec) ->
            chunks += EmbeddingIndex.Chunk(path, line, "snippet", vec)
        }
        return index
    }
}

private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}

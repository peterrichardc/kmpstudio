package studio.kmp.agent.search

import studio.kmp.shared.model.SearchHit
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Tests for SemanticSearch.grepProject — the grep-based fallback path that runs
 * without any AI provider. Vector search path is tested separately in EmbeddingIndexTest.
 */
class SemanticSearchTest {

    private lateinit var tempDir: File
    // SemanticSearch requires an AiProxy which has a real HTTP client — stub via reflection
    private val search = stubSemanticSearch()

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("kmp-search-test").toFile()
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ── Basic matching ────────────────────────────────────────────────────────────

    @Test
    fun `grepProject finds token in Kotlin file`() {
        tempDir.resolve("Foo.kt").writeText("fun connectToServer() {}")
        val hits = search.grepProject("connectToServer", tempDir.absolutePath)
        assertEquals(1, hits.size)
        assertEquals("Foo.kt", hits[0].filePath)
        assertEquals(1, hits[0].lineNumber)
    }

    @Test
    fun `grepProject is case-insensitive`() {
        tempDir.resolve("Bar.kt").writeText("val DATABASE_URL = \"jdbc:sqlite\"")
        val hits = search.grepProject("database_url", tempDir.absolutePath)
        assertEquals(1, hits.size)
    }

    @Test
    fun `grepProject matches any token in multi-token query`() {
        val file = tempDir.resolve("Multi.kt")
        file.writeText("fun saveUser() {}\nfun loadConfig() {}")
        val hits = search.grepProject("saveUser loadConfig", tempDir.absolutePath)
        assertEquals(2, hits.size)
    }

    @Test
    fun `grepProject returns correct line numbers`() {
        tempDir.resolve("Lines.kt").writeText("line1\nline2\ntarget line\nline4")
        val hits = search.grepProject("target", tempDir.absolutePath)
        assertEquals(1, hits.size)
        assertEquals(3, hits[0].lineNumber)
    }

    @Test
    fun `grepProject snippet is trimmed and capped at 200 chars`() {
        val longLine = " ".repeat(10) + "x".repeat(300)
        tempDir.resolve("Long.kt").writeText(longLine)
        val hits = search.grepProject("x".repeat(10), tempDir.absolutePath)
        assertTrue(hits[0].snippet.length <= 200)
        assertFalse(hits[0].snippet.startsWith(" "), "Snippet should be trimmed")
    }

    // ── Edge cases ────────────────────────────────────────────────────────────────

    @Test
    fun `grepProject returns empty list for empty query`() {
        tempDir.resolve("Empty.kt").writeText("fun foo() {}")
        assertTrue(search.grepProject("", tempDir.absolutePath).isEmpty())
    }

    @Test
    fun `grepProject returns empty list for single-char token`() {
        tempDir.resolve("Single.kt").writeText("x = 1")
        assertTrue(search.grepProject("x", tempDir.absolutePath).isEmpty())
    }

    @Test
    fun `grepProject returns empty list for non-existent root`() {
        assertTrue(search.grepProject("anything", "/nonexistent/path").isEmpty())
    }

    @Test
    fun `grepProject skips build directory`() {
        val buildDir = tempDir.resolve("build").also { it.mkdirs() }
        buildDir.resolve("Generated.kt").writeText("fun generated() {}")
        val hits = search.grepProject("generated", tempDir.absolutePath)
        assertTrue(hits.isEmpty(), "Should not search inside build/")
    }

    @Test
    fun `grepProject skips dot-git directory`() {
        val gitDir = tempDir.resolve(".git").also { it.mkdirs() }
        gitDir.resolve("COMMIT_EDITMSG").writeText("feat: add searchToken")
        val hits = search.grepProject("searchToken", tempDir.absolutePath)
        assertTrue(hits.isEmpty(), "Should not search inside .git/")
    }

    @Test
    fun `grepProject caps results at 40`() {
        repeat(50) { i -> tempDir.resolve("F$i.kt").writeText("fun target$i() {}") }
        val hits = search.grepProject("target", tempDir.absolutePath)
        assertTrue(hits.size <= 40)
    }

    // ── File type filtering ───────────────────────────────────────────────────────

    @Test
    fun `grepProject searches kt and kts files`() {
        tempDir.resolve("App.kt").writeText("fun appToken() {}")
        tempDir.resolve("Build.kts").writeText("appToken = true")
        val hits = search.grepProject("appToken", tempDir.absolutePath)
        assertEquals(2, hits.size)
    }

    @Test
    fun `grepProject skips binary-like extensions`() {
        tempDir.resolve("image.png").writeBytes(ByteArray(10) { 0xFF.toByte() })
        val hits = search.grepProject("FF", tempDir.absolutePath)
        assertTrue(hits.isEmpty())
    }

    // ── Relative paths ────────────────────────────────────────────────────────────

    @Test
    fun `grepProject reports relative file paths`() {
        val sub = tempDir.resolve("pkg").also { it.mkdirs() }
        sub.resolve("Nested.kt").writeText("fun nestedToken() {}")
        val hits = search.grepProject("nestedToken", tempDir.absolutePath)
        assertEquals(1, hits.size)
        assertEquals("pkg${File.separator}Nested.kt", hits[0].filePath)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun stubSemanticSearch(): SemanticSearch {
        // AiProxy requires FileSystemAgent; we instantiate via reflection to avoid a real HTTP client
        val fsAgentClass  = Class.forName("studio.kmp.agent.executor.FileSystemAgent")
        val aiProxyClass  = Class.forName("studio.kmp.agent.ai.AiProxy")
        val fsAgent       = fsAgentClass.getDeclaredConstructor().newInstance()
        val aiProxy       = aiProxyClass.getDeclaredConstructor(fsAgentClass).newInstance(fsAgent)
        return SemanticSearch(aiProxy as studio.kmp.agent.ai.AiProxy)
    }
}

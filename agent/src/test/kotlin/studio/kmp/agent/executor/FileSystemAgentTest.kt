package studio.kmp.agent.executor

import studio.kmp.shared.model.DiffHunk
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class FileSystemAgentTest {

    private val agent = FileSystemAgent()
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("kmpstudio-test").toFile()
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ── applyDiff ─────────────────────────────────────────────────────────────────

    @Test
    fun `applyDiff replaces lines in existing file`() {
        val file = tempDir.resolve("Test.kt").also {
            it.writeText("line1\nline2\nline3\n")
        }
        val hunk = DiffHunk(startLine = 2, oldLines = listOf("line2"), newLines = listOf("replaced"))

        val result = agent.applyDiff(file.absolutePath, listOf(hunk), null)

        assertTrue(result)
        assertEquals("line1\nreplaced\nline3", file.readText())
    }

    @Test
    fun `applyDiff adds lines at position`() {
        val file = tempDir.resolve("Add.kt").also {
            it.writeText("first\nlast\n")
        }
        val hunk = DiffHunk(startLine = 2, oldLines = emptyList(), newLines = listOf("middle"))

        agent.applyDiff(file.absolutePath, listOf(hunk), null)

        assertEquals("first\nmiddle\nlast", file.readText())
    }

    @Test
    fun `applyDiff removes lines`() {
        val file = tempDir.resolve("Remove.kt").also {
            it.writeText("keep\nremove this\nalso keep\n")
        }
        val hunk = DiffHunk(startLine = 2, oldLines = listOf("remove this"), newLines = emptyList())

        agent.applyDiff(file.absolutePath, listOf(hunk), null)

        assertEquals("keep\nalso keep", file.readText())
    }

    @Test
    fun `applyDiff applies multiple hunks bottom-up`() {
        val file = tempDir.resolve("Multi.kt").also {
            it.writeText("a\nb\nc\nd\ne\n")
        }
        // Two hunks: first changes line 1, second changes line 4
        // Applied bottom-up: line 4 first, then line 1
        val hunks = listOf(
            DiffHunk(startLine = 1, oldLines = listOf("a"), newLines = listOf("A")),
            DiffHunk(startLine = 4, oldLines = listOf("d"), newLines = listOf("D"))
        )

        agent.applyDiff(file.absolutePath, hunks, null)

        assertEquals("A\nb\nc\nD\ne", file.readText())
    }

    @Test
    fun `applyDiff returns false for non-existent file`() {
        val result = agent.applyDiff("/nonexistent/path/File.kt", emptyList(), null)
        assertFalse(result)
    }

    @Test
    fun `applyDiff resolves relative path against projectRoot`() {
        val file = tempDir.resolve("Relative.kt").also { it.writeText("original\n") }
        val hunk = DiffHunk(startLine = 1, oldLines = listOf("original"), newLines = listOf("changed"))

        agent.applyDiff("Relative.kt", listOf(hunk), tempDir.absolutePath)

        assertEquals("changed", file.readText())
    }

    // ── listDirectory ─────────────────────────────────────────────────────────────

    @Test
    fun `listDirectory returns directories before files`() {
        tempDir.resolve("z_file.kt").writeText("")
        tempDir.resolve("a_dir").mkdir()

        val entries = agent.listDirectory(tempDir.absolutePath)

        assertTrue(entries.first().isDirectory, "First entry should be a directory")
        assertFalse(entries.last().isDirectory, "Last entry should be a file")
    }

    @Test
    fun `listDirectory returns empty list for non-existent path`() {
        val entries = agent.listDirectory("/nonexistent/path")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `listDirectory includes name and path`() {
        tempDir.resolve("Sample.kt").writeText("content")
        val entries = agent.listDirectory(tempDir.absolutePath)
        val file = entries.first()
        assertEquals("Sample.kt", file.name)
        assertTrue(file.path.endsWith("Sample.kt"))
    }

    // ── readFile ──────────────────────────────────────────────────────────────────

    @Test
    fun `readFile returns content of existing file`() {
        val file = tempDir.resolve("Read.kt").also { it.writeText("hello") }
        assertEquals("hello", agent.readFile(file.absolutePath))
    }

    @Test
    fun `readFile returns null for non-existent file`() {
        assertNull(agent.readFile("/nonexistent/File.kt"))
    }

    // ── writeFile ─────────────────────────────────────────────────────────────────

    @Test
    fun `writeFile creates file with content`() {
        val path = tempDir.resolve("New.kt").absolutePath
        agent.writeFile(path, "new content")
        assertEquals("new content", File(path).readText())
    }

    @Test
    fun `writeFile overwrites existing content`() {
        val file = tempDir.resolve("Overwrite.kt").also { it.writeText("old") }
        agent.writeFile(file.absolutePath, "new")
        assertEquals("new", file.readText())
    }
}

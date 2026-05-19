package studio.kmp.agent.ai

import studio.kmp.shared.model.DiffHunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiffParserTest {

    // ── parseDiffs ────────────────────────────────────────────────────────────────

    @Test
    fun `parseDiffs extracts single diff block`() {
        val text = """
            Some explanation text.
            DIFF_START
            FILE: src/Main.kt
            @@ -1,3 +1,3 @@
             unchanged
            -old line
            +new line
            DIFF_END
        """.trimIndent()

        val diffs = DiffParser.parseDiffs(text)
        assertEquals(1, diffs.size)
        assertEquals("src/Main.kt", diffs[0].filePath)
        assertEquals(1, diffs[0].hunks.size)
    }

    @Test
    fun `parseDiffs extracts multiple diff blocks`() {
        val text = """
            DIFF_START
            FILE: src/A.kt
            @@ -1,1 +1,1 @@
            -old
            +new
            DIFF_END
            DIFF_START
            FILE: src/B.kt
            @@ -5,1 +5,1 @@
            -removed
            +added
            DIFF_END
        """.trimIndent()

        val diffs = DiffParser.parseDiffs(text)
        assertEquals(2, diffs.size)
        assertEquals("src/A.kt", diffs[0].filePath)
        assertEquals("src/B.kt", diffs[1].filePath)
    }

    @Test
    fun `parseDiffs returns empty list when no diff blocks present`() {
        val diffs = DiffParser.parseDiffs("Just some AI text with no diffs.")
        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `parseDiffs skips block with no FILE header`() {
        val text = """
            DIFF_START
            @@ -1,1 +1,1 @@
            -old
            +new
            DIFF_END
        """.trimIndent()

        val diffs = DiffParser.parseDiffs(text)
        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `parseDiffs skips block with no hunks`() {
        val text = """
            DIFF_START
            FILE: src/Empty.kt
            DIFF_END
        """.trimIndent()

        val diffs = DiffParser.parseDiffs(text)
        assertTrue(diffs.isEmpty())
    }

    // ── parseHunks ────────────────────────────────────────────────────────────────

    @Test
    fun `parseHunks extracts start line and old and new lines`() {
        val block = """
            FILE: src/Foo.kt
            @@ -10,3 +10,3 @@
             context
            -removed line
            +added line
        """.trimIndent()

        val hunks = DiffParser.parseHunks(block)
        assertEquals(1, hunks.size)
        assertEquals(10, hunks[0].startLine)
        assertEquals(listOf("removed line"), hunks[0].oldLines)
        assertEquals(listOf("added line"), hunks[0].newLines)
    }

    @Test
    fun `parseHunks handles addition-only hunk`() {
        val block = """
            FILE: src/Foo.kt
            @@ -5,0 +5,2 @@
            +first new line
            +second new line
        """.trimIndent()

        val hunks = DiffParser.parseHunks(block)
        assertEquals(1, hunks.size)
        assertTrue(hunks[0].oldLines.isEmpty())
        assertEquals(2, hunks[0].newLines.size)
    }

    @Test
    fun `parseHunks handles multiple hunks in one block`() {
        val block = """
            FILE: src/Foo.kt
            @@ -1,1 +1,1 @@
            -old1
            +new1
            @@ -20,1 +20,1 @@
            -old2
            +new2
        """.trimIndent()

        val hunks = DiffParser.parseHunks(block)
        assertEquals(2, hunks.size)
        assertEquals(1, hunks[0].startLine)
        assertEquals(20, hunks[1].startLine)
    }

    // ── parsePlan ─────────────────────────────────────────────────────────────────

    @Test
    fun `parsePlan extracts summary and steps`() {
        val text = """
            PLAN_START
            SUMMARY: Add retry logic to the network layer
            FILE: src/NetworkClient.kt | Add retry interceptor
            FILE: src/Config.kt | Add maxRetries constant
            PLAN_END
        """.trimIndent()

        val plan = DiffParser.parsePlan(text, "conv-1")
        assertNotNull(plan)
        assertEquals("Add retry logic to the network layer", plan.summary)
        assertEquals(2, plan.steps.size)
        assertEquals("src/NetworkClient.kt", plan.steps[0].filePath)
        assertEquals("Add retry interceptor", plan.steps[0].description)
    }

    @Test
    fun `parsePlan returns null when no PLAN block present`() {
        val plan = DiffParser.parsePlan("Just some text", "conv-1")
        assertNull(plan)
    }

    @Test
    fun `parsePlan uses default summary when SUMMARY missing`() {
        val text = """
            PLAN_START
            FILE: src/A.kt | Do something
            PLAN_END
        """.trimIndent()

        val plan = DiffParser.parsePlan(text, "conv-1")
        assertNotNull(plan)
        assertEquals("Change plan", plan.summary)
    }

    // ── extractFirstJsonObject ────────────────────────────────────────────────────

    @Test
    fun `extractFirstJsonObject finds bare JSON object`() {
        val json = DiffParser.extractFirstJsonObject("""{"key":"value"}""")
        assertEquals("""{"key":"value"}""", json)
    }

    @Test
    fun `extractFirstJsonObject ignores text before and after`() {
        val json = DiffParser.extractFirstJsonObject("""Here is the result: {"a":1} done.""")
        assertEquals("""{"a":1}""", json)
    }

    @Test
    fun `extractFirstJsonObject handles nested objects`() {
        val json = DiffParser.extractFirstJsonObject("""{"outer":{"inner":42}}""")
        assertEquals("""{"outer":{"inner":42}}""", json)
    }

    @Test
    fun `extractFirstJsonObject returns null when no object present`() {
        val json = DiffParser.extractFirstJsonObject("no json here")
        assertNull(json)
    }
}

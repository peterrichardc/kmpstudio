package studio.kmp.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CliOutputParserTest {

    private val parser = CliOutputParser()

    @Test
    fun `error prefix e colon is classified as ERROR`() {
        val result = parser.classify("e: /src/Main.kt: (10, 5): Unresolved reference: foo")
        assertEquals(LineType.ERROR, result.type)
    }

    @Test
    fun `error prefix error colon is classified as ERROR`() {
        val result = parser.classify("error: compilation failed")
        assertEquals(LineType.ERROR, result.type)
    }

    @Test
    fun `BUILD SUCCESSFUL line is classified as SUCCESS`() {
        val result = parser.classify("BUILD SUCCESSFUL in 42s")
        assertEquals(LineType.SUCCESS, result.type)
    }

    @Test
    fun `warning prefix is classified as WARNING`() {
        val result = parser.classify("w: /src/Foo.kt: (3, 1): Deprecated API")
        assertEquals(LineType.WARNING, result.type)
    }

    @Test
    fun `regular output line is classified as INFO`() {
        val result = parser.classify("> Task :agent:compileKotlin")
        assertEquals(LineType.INFO, result.type)
    }

    @Test
    fun `kotlin compiler error format extracts BuildError`() {
        val line = "e: file:///src/Main.kt: (10, 5): Unresolved reference: foo"
        val result = parser.classify(line)
        assertEquals(LineType.ERROR, result.type)
        assertNotNull(result.buildError)
        assertEquals(10, result.buildError!!.line)
        assertEquals(5, result.buildError!!.col)
    }

    @Test
    fun `gcc style error format extracts BuildError`() {
        // Must start with "e: " so the line is classified as ERROR before parseBuildError runs
        val line = "e: /src/Main.kt:42:7: error: type mismatch"
        val result = parser.classify(line)
        assertEquals(LineType.ERROR, result.type)
        assertNotNull(result.buildError)
        assertEquals(42, result.buildError!!.line)
        assertEquals(7, result.buildError!!.col)
        assertEquals("type mismatch", result.buildError!!.message)
    }

    @Test
    fun `non-error line has null BuildError`() {
        val result = parser.classify("> Task :compileKotlin UP-TO-DATE")
        assertNull(result.buildError)
    }

    @Test
    fun `raw text is preserved in ParsedLine`() {
        val line = "some output line"
        val result = parser.classify(line)
        assertEquals(line, result.raw)
    }
}

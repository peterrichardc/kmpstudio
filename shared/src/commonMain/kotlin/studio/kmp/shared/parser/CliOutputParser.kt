package studio.kmp.shared.parser

class CliOutputParser {
    private val errorRegex   = Regex("""^(e|error):\s.+""",   RegexOption.IGNORE_CASE)
    private val warningRegex = Regex("""^(w|warning):\s.+""", RegexOption.IGNORE_CASE)
    private val successRegex = Regex("""BUILD SUCCESSFUL in \d+""")

    // e: /path/File.kt: (10, 5): Unresolved reference  (Kotlin compiler)
    private val kotlinErrorRegex = Regex("""^e:\s+(?:file://)?(.+?):\s+\((\d+),\s*(\d+)\):\s+(.+)$""")
    // /path/File.kt:10:5: error: message  (GCC-style)
    private val gccErrorRegex    = Regex("""^(.+\.(?:kt|java|swift|m|cpp|c|h)):(\d+):(\d+):\s+(?:error|Error):\s+(.+)$""")

    fun classify(line: String): ParsedLine {
        val type = when {
            errorRegex.containsMatchIn(line)   -> LineType.ERROR
            warningRegex.containsMatchIn(line) -> LineType.WARNING
            successRegex.containsMatchIn(line) -> LineType.SUCCESS
            else                               -> LineType.INFO
        }
        val buildError = if (type == LineType.ERROR) parseBuildError(line) else null
        return ParsedLine(line, type, buildError)
    }

    private fun parseBuildError(line: String): BuildError? {
        kotlinErrorRegex.find(line)?.let { m ->
            val lineNum = m.groupValues[2].toIntOrNull() ?: return null
            val colNum  = m.groupValues[3].trim().toIntOrNull() ?: return null
            return BuildError(m.groupValues[1].trim(), lineNum, colNum, m.groupValues[4].trim())
        }
        gccErrorRegex.find(line)?.let { m ->
            val lineNum = m.groupValues[2].toIntOrNull() ?: return null
            val colNum  = m.groupValues[3].toIntOrNull() ?: return null
            return BuildError(m.groupValues[1].trim(), lineNum, colNum, m.groupValues[4].trim())
        }
        return null
    }
}

data class BuildError(val filePath: String, val line: Int, val col: Int, val message: String)
data class ParsedLine(val raw: String, val type: LineType, val buildError: BuildError? = null)
enum class LineType { INFO, WARNING, ERROR, SUCCESS }

package studio.kmp.agent.ai

import studio.kmp.shared.model.*

object DiffParser {

    fun parseDiffs(text: String): List<WsMessage.DiffSuggestion> =
        Regex("""DIFF_START\n(.*?)DIFF_END""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .mapNotNull { match ->
                val block = match.groupValues[1]
                val path = Regex("""FILE:\s(.+)""").find(block)?.groupValues?.get(1)?.trim()
                    ?: return@mapNotNull null
                val hunks = parseHunks(block)
                if (hunks.isEmpty()) null else WsMessage.DiffSuggestion(path, hunks)
            }.toList()

    fun parseHunks(block: String): List<DiffHunk> =
        Regex("""@@\s-(\d+)(?:,\d+)?\s\+\d+(?:,\d+)?\s@@\n(.*?)(?=@@|\z)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(block)
            .mapNotNull { m ->
                val startLine = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val lines = m.groupValues[2].lines()
                DiffHunk(
                    startLine = startLine,
                    oldLines  = lines.filter { it.startsWith("-") }.map { it.drop(1) },
                    newLines  = lines.filter { it.startsWith("+") }.map { it.drop(1) }
                )
            }.toList()

    fun parsePlan(text: String, conversationId: String): WsMessage.PlanSuggestion? {
        val block = Regex("""PLAN_START\n(.*?)PLAN_END""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1) ?: return null
        val lines   = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val summary = lines.firstOrNull { it.startsWith("SUMMARY:") }
            ?.removePrefix("SUMMARY:")?.trim() ?: "Change plan"
        val steps = lines
            .filter { it.startsWith("FILE:") }
            .mapNotNull { line ->
                val parts = line.removePrefix("FILE:").trim().split("|", limit = 2)
                if (parts.isEmpty()) null
                else PlanStep(filePath = parts[0].trim(), description = parts.getOrNull(1)?.trim() ?: "")
            }
        return WsMessage.PlanSuggestion(conversationId, summary, steps)
    }

    /** Finds the first complete JSON object by tracking brace depth — handles nested objects correctly. */
    fun extractFirstJsonObject(text: String): String? {
        var depth = 0
        var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '{' -> { if (depth++ == 0) start = i }
                '}' -> { if (--depth == 0 && start >= 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    /** Finds the first complete JSON array by tracking bracket depth. */
    fun extractFirstJsonArray(text: String): String? {
        var depth = 0
        var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '[' -> { if (depth++ == 0) start = i }
                ']' -> { if (--depth == 0 && start >= 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
}

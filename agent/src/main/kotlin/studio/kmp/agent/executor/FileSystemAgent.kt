package studio.kmp.agent.executor

import studio.kmp.shared.model.DiffHunk
import studio.kmp.shared.model.FileEntry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileSystemAgent {
    private val maxBytesPerFile = 100_000L

    fun readFilesAsContext(paths: List<String>): String = buildString {
        paths.forEach { path ->
            val file = File(path)
            if (!file.exists() || !file.isFile) return@forEach
            if (file.length() > maxBytesPerFile) {
                appendLine("// [SKIPPED — file too large: $path (${file.length()} bytes)]")
                return@forEach
            }
            val ext = file.extension.ifBlank { "kotlin" }
            appendLine("```$ext")
            appendLine("// FILE: $path")
            appendLine(file.readText())
            appendLine("```")
        }
    }

    fun listDirectory(path: String): List<FileEntry> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?.map { FileEntry(it.name, it.absolutePath, it.isDirectory, it.length()) }
            ?: emptyList()
    }

    fun readFile(path: String): String? {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() > maxBytesPerFile) return null
        return file.readText()
    }

    fun writeFile(path: String, content: String) = File(path).writeText(content)

    fun applyDiff(filePath: String, hunks: List<DiffHunk>, projectRoot: String?): Boolean {
        val resolved = when {
            File(filePath).isAbsolute -> File(filePath)
            projectRoot != null       -> File(projectRoot, filePath)
            else                      -> return false
        }
        if (!resolved.exists() || !resolved.isFile) return false
        val lines = resolved.readLines().toMutableList()
        // Apply from bottom up so earlier hunks don't shift subsequent line numbers
        for (hunk in hunks.sortedByDescending { it.startLine }) {
            val start = (hunk.startLine - 1).coerceAtLeast(0)
            val end   = (start + hunk.oldLines.size).coerceAtMost(lines.size)
            lines.subList(start, end).clear()
            lines.addAll(start, hunk.newLines)
        }
        val tmp = File.createTempFile("diff-", ".tmp", resolved.parentFile)
        try {
            tmp.writeText(lines.joinToString("\n"))
            try {
                Files.move(tmp.toPath(), resolved.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), resolved.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        return true
    }

    fun gatherAutoContext(activeFilePath: String?, projectRoot: String?): String = buildString {
        val root = projectRoot?.let { File(it) }

        // ── Build config ──────────────────────────────────────────────────────
        val buildFiles = mutableListOf<File>()
        if (root != null) {
            listOf("settings.gradle.kts", "build.gradle.kts").forEach { name ->
                root.resolve(name).takeIf { it.exists() }?.let { buildFiles += it }
            }
            root.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.forEach { module ->
                    module.resolve("build.gradle.kts").takeIf { it.exists() }?.let { buildFiles += it }
                }
        } else if (activeFilePath != null) {
            // Walk up until settings.gradle.kts is found (project root heuristic)
            var dir = File(activeFilePath).parentFile
            while (dir != null) {
                val settings = dir.resolve("settings.gradle.kts")
                val build    = dir.resolve("build.gradle.kts")
                if (settings.exists()) { buildFiles += settings; build.takeIf { it.exists() }?.let { buildFiles += it }; break }
                build.takeIf { it.exists() }?.let { buildFiles += it }
                dir = dir.parentFile
            }
        }
        if (buildFiles.isNotEmpty()) {
            appendLine("## Build Configuration")
            append(readFilesAsContext(buildFiles.map { it.absolutePath }))
        }

        // ── Files referenced by imports in the active file ────────────────────
        if (activeFilePath != null && root != null) {
            val activeFile = File(activeFilePath)
            if (activeFile.exists()) {
                val srcSetDirs = listOf(
                    "src/commonMain/kotlin", "src/main/kotlin",
                    "src/wasmJsMain/kotlin", "src/jvmMain/kotlin",
                    "src/androidMain/kotlin", "src/iosMain/kotlin"
                )
                val imports = activeFile.readLines()
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").trim() }

                val resolved = mutableListOf<String>()
                for (import in imports) {
                    val relPath = import.replace('.', '/') + ".kt"
                    outer@ for (module in (root.listFiles()?.filter { it.isDirectory } ?: emptyList()) + listOf(root)) {
                        for (srcDir in srcSetDirs) {
                            val candidate = module.resolve("$srcDir/$relPath")
                            if (candidate.exists() && candidate.absolutePath != activeFilePath) {
                                resolved += candidate.absolutePath
                                break@outer
                            }
                        }
                    }
                    if (resolved.size >= 5) break
                }
                if (resolved.isNotEmpty()) {
                    appendLine("## Referenced Files")
                    append(readFilesAsContext(resolved))
                }
            }
        }
    }
}


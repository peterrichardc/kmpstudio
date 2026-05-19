package studio.kmp.frontend.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EditorState {
    val openFiles = mutableStateMapOf<String, String>()  // path → content
    var activeFile by mutableStateOf<String?>(null)

    fun openFile(path: String, content: String) {
        openFiles[path] = content
        activeFile = path
    }

    fun updateContent(path: String, content: String) {
        if (path in openFiles) openFiles[path] = content
    }

    fun closeFile(path: String) {
        openFiles.remove(path)
        if (activeFile == path) {
            activeFile = openFiles.keys.lastOrNull()
        }
    }

    fun fileName(path: String): String = path.substringAfterLast('/')

    fun language(path: String): String = when {
        path.endsWith(".gradle.kts") -> "kotlin"
        path.endsWith(".kt") || path.endsWith(".kts") -> "kotlin"
        path.endsWith(".xml")  -> "xml"
        path.endsWith(".json") -> "json"
        path.endsWith(".toml") -> "ini"
        path.endsWith(".md")   -> "markdown"
        else -> "plaintext"
    }
}

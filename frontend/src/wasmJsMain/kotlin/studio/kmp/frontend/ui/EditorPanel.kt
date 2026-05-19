package studio.kmp.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.sp
import studio.kmp.frontend.interop.*
import studio.kmp.frontend.theme.KmpBase
import studio.kmp.frontend.theme.KmpOverlay0

@Composable
fun EditorPanel(
    editorId:        String,
    initialContent:  String,
    language:        String = "kotlin",
    modifier:        Modifier = Modifier,
    onContentPoll:   (getValue: () -> String) -> Unit = {}
) {
    var initialized by remember { mutableStateOf(false) }

    // Expose a getter that the parent can call any time it needs the current text
    LaunchedEffect(editorId) {
        onContentPoll { monacoGetValue(editorId) }
    }

    Box(
        modifier = modifier
            .background(KmpBase)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val w   = coords.size.width
                val h   = coords.size.height
                if (!initialized) {
                    // Create the DOM host div on first layout pass
                    createEditorContainer(editorId, pos.x.toInt(), pos.y.toInt(), w, h)
                    monacoInit(editorId, initialContent, language)
                    initialized = true
                } else {
                    updateEditorContainer(editorId, pos.x.toInt(), pos.y.toInt(), w, h)
                }
            }
    ) {
        // Shown only until the Monaco container appears on top
        if (!initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading editor...", color = KmpOverlay0, fontSize = 13.sp)
            }
        }
    }

    DisposableEffect(editorId) {
        onDispose { monacoDispose(editorId) }
    }
}

package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import studio.kmp.frontend.interop.monacoSetValue
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.WsMessage

@Composable
fun DiffHistoryPanel(
    entries:   List<DiffHistoryEntry>,
    state:     IdeState,
    modifier:  Modifier = Modifier
) {
    val scroll = rememberScrollState()

    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KmpMantle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AI Diff History",
                color      = KmpSubtext,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Text(
                    "${entries.size} change${if (entries.size != 1) "s" else ""}",
                    color    = KmpOverlay0,
                    fontSize = 11.sp
                )
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No AI changes yet", color = KmpOverlay0, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Changes applied by the AI will appear here",
                        color    = KmpOverlay0.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entries.forEach { entry ->
                    DiffHistoryItem(entry = entry, state = state)
                }
            }
        }
    }
}

@Composable
private fun DiffHistoryItem(entry: DiffHistoryEntry, state: IdeState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KmpSurface0)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.fileName,
                color      = KmpText,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                entry.timestamp,
                color    = KmpOverlay0,
                fontSize = 11.sp
            )
        }

        // Revert button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(KmpSurface1)
                .clickable { revert(entry, state) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Revert",
                color      = KmpRed,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun revert(entry: DiffHistoryEntry, state: IdeState) {
    // Restore original content in editor and on disk
    if (entry.filePath == state.editorState.activeFile) {
        monacoSetValue(state.editorId, entry.originalContent)
        state.editorState.updateContent(entry.filePath, entry.originalContent)
    } else {
        state.editorState.openFiles[entry.filePath] = entry.originalContent
    }
    state.wsClient.send(WsMessage.WriteFile(entry.filePath, entry.originalContent))
    state.diffHistory.remove(entry)
}

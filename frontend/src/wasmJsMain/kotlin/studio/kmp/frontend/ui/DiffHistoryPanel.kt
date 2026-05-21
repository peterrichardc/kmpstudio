package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import studio.kmp.frontend.theme.*

@Composable
fun DiffHistoryPanel(
    entries:  List<DiffHistoryEntry>,
    onRevert: (DiffHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DiffHistoryItem(entry = entry, onRevert = onRevert)
                }
            }
        }
    }
}

@Composable
private fun DiffHistoryItem(
    entry:    DiffHistoryEntry,
    onRevert: (DiffHistoryEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KmpSurface0)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
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
            Text(entry.timestamp, color = KmpOverlay0, fontSize = 11.sp)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(KmpSurface1)
                .clickable { onRevert(entry) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Revert", color = KmpRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

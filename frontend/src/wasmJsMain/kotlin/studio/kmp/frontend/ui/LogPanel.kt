package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import studio.kmp.frontend.interop.copyToClipboard
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.StreamType
import studio.kmp.shared.parser.LineType
import studio.kmp.shared.parser.ParsedLine

data class LogEntry(
    val text: String,
    val lineType: LineType,
    val stream: StreamType = StreamType.STDOUT
)

@Composable
fun LogPanel(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onFixWithAi: ((String) -> Unit)? = null,
    onInterpretWithAi: ((String) -> Unit)? = null,
    isAiProcessing: Boolean = false
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new entries
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(modifier = modifier.background(KmpMantle)) {
        // ── Toolbar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(KmpCrust)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BUILD OUTPUT", color = KmpOverlay0, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { copyToClipboard(entries.joinToString("\n") { it.text }) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
            ) { Text("Copy", fontSize = 11.sp) }
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
            ) { Text("Clear", fontSize = 11.sp) }
        }

        // ── Log lines ──────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state    = listState,
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(entries) { entry ->
                    LogLine(entry, onFixWithAi)
                }
            }
        }

        // ── Status bar ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(KmpCrust)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val errors   = entries.count { it.lineType == LineType.ERROR }
            val warnings = entries.count { it.lineType == LineType.WARNING }
            if (errors > 0)
                StatusChip("x $errors error${if (errors > 1) "s" else ""}", KmpRed)
            if (warnings > 0) {
                Spacer(Modifier.width(8.dp))
                StatusChip("! $warnings warning${if (warnings > 1) "s" else ""}", KmpYellow)
            }
            if (errors == 0 && warnings == 0 && entries.isNotEmpty())
                StatusChip("OK", KmpGreen)
            Spacer(Modifier.weight(1f))
            if (onInterpretWithAi != null && entries.isNotEmpty()) {
                TextButton(
                    onClick        = { onInterpretWithAi(entries.joinToString("\n") { it.text }) },
                    enabled        = !isAiProcessing,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors         = ButtonDefaults.textButtonColors(contentColor = KmpPurple)
                ) {
                    Text(
                        if (isAiProcessing) "Analyzing..." else "Analyze AI",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry, onFixWithAi: ((String) -> Unit)?) {
    val color = when (entry.lineType) {
        LineType.ERROR   -> KmpRed
        LineType.WARNING -> KmpYellow
        LineType.SUCCESS -> KmpGreen
        LineType.INFO    -> if (entry.stream == StreamType.STDERR) KmpPeach else KmpSubtext
    }
    if (entry.lineType == LineType.ERROR && onFixWithAi != null) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text       = entry.text,
                color      = color,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier   = Modifier.weight(1f)
            )
            TextButton(
                onClick        = { onFixWithAi(entry.text) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier       = Modifier.height(20.dp),
                colors         = ButtonDefaults.textButtonColors(contentColor = KmpBlue)
            ) { Text("Fix", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
    } else {
        Text(
            text       = entry.text,
            color      = color,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            modifier   = Modifier.fillMaxWidth().padding(vertical = 1.dp)
        )
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

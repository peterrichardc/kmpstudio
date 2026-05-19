package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.delay
import studio.kmp.frontend.interop.copyToClipboard
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.ChatMessage

@Composable
fun ChatPanel(
    history:      List<ChatMessage>,
    value:        String,
    onChange:     (String) -> Unit,
    onSend:       () -> Unit,
    onClear:      () -> Unit,
    isProcessing: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth().background(KmpCrust)) {
        if (history.isNotEmpty() || isProcessing) {
            val listState = rememberLazyListState()
            val itemCount = history.size + if (isProcessing) 1 else 0
            LaunchedEffect(itemCount) {
                if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
            }
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                itemsIndexed(history) { index, msg ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(1.dp)
                                .background(KmpSurface0.copy(alpha = 0.5f))
                        )
                    }
                    ChatMessageItem(msg)
                }
                if (isProcessing) {
                    item { ThinkingIndicator() }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KmpSurface0))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI", color = KmpPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (history.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                val turns = (history.size + 1) / 2
                Text(
                    "$turns turn${if (turns > 1) "s" else ""}",
                    color = KmpOverlay0, fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = value, onValueChange = onChange, singleLine = true,
                placeholder = { Text("Ask AI to modify this file...", color = KmpOverlay0, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = KmpPurple,
                    unfocusedBorderColor = KmpSurface0,
                    focusedTextColor     = KmpText,
                    unfocusedTextColor   = KmpText,
                    cursorColor          = KmpPurple
                )
            )
            Spacer(Modifier.width(8.dp))
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
                ) { Text("Clear", fontSize = 11.sp) }
                Spacer(Modifier.width(4.dp))
            }
            Button(
                onClick  = onSend,
                enabled  = !isProcessing,
                colors   = ButtonDefaults.buttonColors(containerColor = KmpPurple, contentColor = KmpCrust)
            ) { Text("Send", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ChatMessageItem(msg: ChatMessage) {
    val isUser = msg.role == "user"
    var copiedFull by remember { mutableStateOf(false) }
    LaunchedEffect(copiedFull) {
        if (copiedFull) { delay(1500); copiedFull = false }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isUser) KmpPurple.copy(alpha = 0.15f) else KmpBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isUser) "You" else "AI",
                    color      = if (isUser) KmpPurple else KmpBlue,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { copyToClipboard(msg.content); copiedFull = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (copiedFull) "copiado!" else "copiar",
                    color    = if (copiedFull) KmpGreen else KmpOverlay0,
                    fontSize = 10.sp
                )
            }
        }

        val segments = parseSegments(msg.content)
        if (segments.isEmpty()) {
            Text(msg.content, color = if (isUser) KmpText else KmpSubtext, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { seg ->
                    when (seg) {
                        is TextPart -> if (seg.text.isNotBlank()) {
                            Text(seg.text.trim(), color = if (isUser) KmpText else KmpSubtext, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                        is CodePart -> CodeBlockView(seg.lang, seg.code)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(lang: String, code: String) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KmpBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KmpMantle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(lang.ifBlank { "código" }, color = KmpOverlay0, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { copyToClipboard(code); copied = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (copied) "copiado!" else "copiar",
                    color    = if (copied) KmpGreen else KmpOverlay0,
                    fontSize = 10.sp
                )
            }
        }
        Text(
            code,
            modifier   = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
            color      = KmpText,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ThinkingIndicator() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(KmpBlue.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "AI está pensando" + ".".repeat(dotCount),
            color    = KmpBlue.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

private sealed class MsgSegment
private data class TextPart(val text: String) : MsgSegment()
private data class CodePart(val lang: String, val code: String) : MsgSegment()

private fun parseSegments(text: String): List<MsgSegment> {
    val result = mutableListOf<MsgSegment>()
    val parts  = text.split("```")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 0) {
            if (part.isNotBlank()) result.add(TextPart(part))
        } else {
            val lines     = part.lines()
            val firstLine = lines.firstOrNull()?.trim() ?: ""
            val hasLangId = firstLine.isNotBlank() && !firstLine.contains(' ')
            val lang      = if (hasLangId) firstLine else ""
            val code      = lines.drop(if (hasLangId) 1 else 0).joinToString("\n").trimEnd()
            if (code.isNotBlank()) result.add(CodePart(lang, code))
        }
    }
    return result
}

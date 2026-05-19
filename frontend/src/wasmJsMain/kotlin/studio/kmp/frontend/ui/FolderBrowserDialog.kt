package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import studio.kmp.frontend.interop.*
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.FileEntry

private val fsJson = Json { ignoreUnknownKeys = true }

@Composable
fun FolderBrowserDialog(
    agentBaseUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // currentPath drives the LaunchedEffect — changing it triggers a new fetch
    var currentPath by remember { mutableStateOf("") }
    var entries     by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var fetchError  by remember { mutableStateOf(false) }

    // Resolve home directory once on open
    LaunchedEffect(Unit) {
        fsFetchHome(agentBaseUrl)
        repeat(80) {
            val home = fsPopHome()
            if (home != null) { currentPath = home; return@LaunchedEffect }
            delay(50)
        }
        currentPath = "/"
    }

    // Re-fetch whenever currentPath changes (empty = still resolving home)
    LaunchedEffect(currentPath) {
        if (currentPath.isEmpty()) return@LaunchedEffect
        loading = true
        fetchError = false
        entries = emptyList()
        fsListDir(agentBaseUrl, currentPath)
        repeat(60) {
            val raw = fsGetListResult()
            if (raw != null) {
                entries = runCatching { fsJson.decodeFromString<List<FileEntry>>(raw) }
                    .getOrDefault(emptyList())
                loading = false
                return@LaunchedEffect
            }
            delay(80)
        }
        loading = false
        fetchError = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .heightIn(min = 400.dp, max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KmpMantle)
                .border(1.dp, KmpSurface0, RoundedCornerShape(16.dp))
                .clickable {}
                .padding(24.dp)
        ) {
            Text(
                "Browse for Folder",
                color = KmpText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))

            BreadcrumbRow(currentPath, onSegmentClick = { currentPath = it })
            Spacer(Modifier.height(10.dp))

            // Entry list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KmpBase)
                    .border(1.dp, KmpSurface0, RoundedCornerShape(8.dp))
            ) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KmpPurple, modifier = Modifier.size(28.dp))
                    }
                    fetchError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Agent unreachable — check if the agent is running", color = KmpRed, fontSize = 13.sp)
                    }
                    else -> {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(6.dp)
                        ) {
                            val parent = parentPath(currentPath)
                            if (parent != null) {
                                EntryRow("..", isDir = true) { currentPath = parent }
                            }
                            if (entries.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("Empty directory", color = KmpOverlay0, fontSize = 13.sp) }
                            }
                            entries.forEach { entry ->
                                EntryRow(
                                    name  = entry.name,
                                    isDir = entry.isDirectory,
                                    onClick = { if (entry.isDirectory) currentPath = entry.path }
                                )
                            }
                        }
                        VerticalScrollbar(
                            adapter  = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected path display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(KmpBase)
                    .border(1.dp, KmpSurface0, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Path: ", color = KmpOverlay0, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text     = currentPath.ifEmpty { "..." },
                    color    = KmpText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = KmpText)
                ) { Text("Cancel") }
                Button(
                    onClick  = { if (currentPath.isNotEmpty()) onConfirm(currentPath) },
                    enabled  = currentPath.isNotEmpty() && !loading,
                    colors   = ButtonDefaults.buttonColors(containerColor = KmpBlue, contentColor = KmpCrust)
                ) { Text("Select Folder") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbRow(path: String, onSegmentClick: (String) -> Unit) {
    val segments = path.trimEnd('/').split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()

    LaunchedEffect(path) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KmpBase)
            .border(1.dp, KmpSurface0, RoundedCornerShape(6.dp))
            .horizontalScroll(scrollState)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BreadcrumbSegment("/", isLast = segments.isEmpty(), onClick = { onSegmentClick("/") })
        segments.forEachIndexed { idx, seg ->
            Text(" / ", color = KmpOverlay0, fontSize = 12.sp)
            val partialPath = "/" + segments.subList(0, idx + 1).joinToString("/")
            BreadcrumbSegment(seg, isLast = idx == segments.lastIndex, onClick = { onSegmentClick(partialPath) })
        }
    }
}

@Composable
private fun BreadcrumbSegment(label: String, isLast: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = when {
            isLast  -> KmpText
            hovered -> KmpBlue
            else    -> KmpOverlay0
        },
        fontSize   = 12.sp,
        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
        modifier   = Modifier
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(horizontal = 2.dp)
    )
}

@Composable
private fun EntryRow(name: String, isDir: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered && isDir) KmpSurface0 else Color.Transparent)
            .then(if (isDir) Modifier.clickable(onClick = onClick) else Modifier)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = if (isDir) "/" else "-",
            color    = if (isDir) KmpYellow else KmpSurface1,
            fontSize = 12.sp,
            modifier = Modifier.width(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text     = name,
            color    = if (isDir) KmpText else KmpOverlay0,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun parentPath(path: String): String? {
    val p = path.trimEnd('/')
    if (!p.contains('/')) return null
    val idx = p.lastIndexOf('/')
    return if (idx == 0) null else p.substring(0, idx)
}

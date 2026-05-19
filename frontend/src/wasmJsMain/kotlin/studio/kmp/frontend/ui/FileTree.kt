package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.FileEntry

@Composable
fun FileTree(
    rootPath: String,
    dirListings: Map<String, List<FileEntry>>,
    expandedDirs: Set<String>,
    selectedFile: String?,
    onExpandDir: (String) -> Unit,
    onSelectFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()

    Box(modifier = modifier.background(KmpMantle)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(KmpCrust)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FILES", color = KmpOverlay0, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }

            // Root entries
            val rootEntries = dirListings[rootPath] ?: emptyList()
            rootEntries.forEach { entry ->
                TreeNode(
                    entry        = entry,
                    depth        = 0,
                    dirListings  = dirListings,
                    expandedDirs = expandedDirs,
                    selectedFile = selectedFile,
                    onExpand     = onExpandDir,
                    onSelect     = onSelectFile
                )
            }
        }
        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun TreeNode(
    entry: FileEntry,
    depth: Int,
    dirListings: Map<String, List<FileEntry>>,
    expandedDirs: Set<String>,
    selectedFile: String?,
    onExpand: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val isExpanded = entry.path in expandedDirs
    val isSelected = entry.path == selectedFile
    var hovered by remember { mutableStateOf(false) }

    val bgColor = when {
        isSelected -> KmpSurface0
        hovered    -> KmpSurface0.copy(alpha = 0.5f)
        else       -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable {
                if (entry.isDirectory) onExpand(entry.path)
                else onSelect(entry.path)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(start = (12 + depth * 14).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chevron / file icon
        Text(
            text = if (entry.isDirectory) (if (isExpanded) "v " else "> ") else "  ",
            color = KmpOverlay0,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = entry.name,
            color = if (isSelected) KmpText else if (entry.isDirectory) KmpSubtext else KmpText.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Recursively render children when expanded
    if (entry.isDirectory && isExpanded) {
        val children = dirListings[entry.path] ?: emptyList()
        children.forEach { child ->
            TreeNode(
                entry        = child,
                depth        = depth + 1,
                dirListings  = dirListings,
                expandedDirs = expandedDirs,
                selectedFile = selectedFile,
                onExpand     = onExpand,
                onSelect     = onSelect
            )
        }
    }
}

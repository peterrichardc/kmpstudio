package studio.kmp.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.kmp.frontend.theme.*
import studio.kmp.shared.model.SearchHit

@Composable
fun SemanticSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    results: List<SearchHit>,
    onResultClick: (SearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 60.dp)
                .width(680.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KmpCrust)
                .clickable(enabled = false, onClick = {})
        ) {
            // ── Search input ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KmpMantle)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search", color = KmpOverlay0, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = KmpText, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(KmpPurple),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Where is X implemented?",
                                color = KmpOverlay0, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            inner()
                        }
                    }
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(start = 8.dp),
                        color = KmpPurple,
                        strokeWidth = 2.dp
                    )
                } else {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KmpPurple.copy(alpha = 0.18f))
                            .clickable(enabled = query.isNotBlank(), onClick = onSearch)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Search", color = KmpPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = KmpSurface0, thickness = 1.dp)

            // ── Results ───────────────────────────────────────────────────────
            if (results.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Type a query and press Search", color = KmpOverlay0, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(results) { hit ->
                        SearchResultItem(hit = hit, onClick = { onResultClick(hit) })
                        HorizontalDivider(color = KmpSurface0.copy(alpha = 0.5f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(hit: SearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hit.filePath,
                color = KmpBlue,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ":${hit.lineNumber}",
                color = KmpOverlay0,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = hit.snippet,
            color = KmpText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (hit.explanation.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hit.explanation,
                color = KmpGreen,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.serialization.Serializable
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ui.wizard.NewProjectWizard
import studio.kmp.frontend.ws.WsClient

@Serializable
data class ProjectState(
    val name: String,
    val path: String,
    val targets: List<String> = listOf("android", "ios", "desktop", "web")
)

@Composable
fun HomeScreen(
    wsClient:       WsClient,
    onProjectReady: (ProjectState) -> Unit,
    onSettings:     () -> Unit
) {
    var showWizard     by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    val recents        by remember { mutableStateOf(SettingsStorage.recentProjects) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KmpBase)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left sidebar ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(KmpMantle)
                    .padding(20.dp)
            ) {
                // Logo
                Spacer(Modifier.height(12.dp))
                KmpLogo()
                Spacer(Modifier.height(32.dp))

                Text("Recent Projects", color = KmpOverlay0, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(12.dp))

                if (recents.isEmpty()) {
                    Text("No recent projects", color = KmpOverlay0, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp))
                } else {
                    recents.forEach { project ->
                        RecentProjectItem(project) { onProjectReady(project) }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                // Settings button at bottom of sidebar
                SidebarButton("Settings", onClick = onSettings)
                Spacer(Modifier.height(8.dp))
                SidebarButton("Documentation", onClick = {})
            }

            // ── Main content ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Hero text
                Text(
                    text = "Welcome to KMP Studio",
                    color = KmpText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "AI-First IDE for Kotlin Multiplatform",
                    color = KmpOverlay0,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(56.dp))

                // Action cards
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionCard(
                        icon       = "+",
                        title      = "New Project",
                        subtitle   = "Create a fresh KMP project",
                        accentColor = KmpPurple,
                        onClick    = { showWizard = true }
                    )
                    ActionCard(
                        icon       = "[ ]",
                        title      = "Open Project",
                        subtitle   = "Open an existing directory",
                        accentColor = KmpBlue,
                        onClick    = { showOpenDialog = true }
                    )
                    ActionCard(
                        icon       = "{ }",
                        title      = "Settings",
                        subtitle   = "API keys & agent config",
                        accentColor = KmpTeal,
                        onClick    = onSettings
                    )
                }

                Spacer(Modifier.height(48.dp))
                VersionBadge()
            }
        }
    }

    if (showWizard) {
        NewProjectWizard(
            wsClient   = wsClient,
            onComplete = { project ->
                showWizard = false
                SettingsStorage.addRecentProject(project)
                onProjectReady(project)
            },
            onCancel = { showWizard = false }
        )
    }

    if (showOpenDialog) {
        OpenProjectDialog(
            agentBaseUrl = "http://127.0.0.1:${SettingsStorage.agentPort}",
            onConfirm    = { project ->
                showOpenDialog = false
                SettingsStorage.addRecentProject(project)
                onProjectReady(project)
            },
            onDismiss = { showOpenDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KmpLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(KmpPurple, KmpBlue))),
            contentAlignment = Alignment.Center
        ) {
            Text("K", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("KMP Studio", color = KmpText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("v0.1.0-alpha", color = KmpOverlay0, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ActionCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovered) KmpSurface0 else KmpMantle)
            .border(
                width = 1.dp,
                color = if (hovered) accentColor.copy(alpha = 0.6f) else KmpSurface0,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        hovered = event.type != PointerEventType.Exit
                    }
                }
            }
            .padding(24.dp)
    ) {
        Column {
            Text(icon, color = accentColor, fontSize = 28.sp)
            Spacer(Modifier.height(16.dp))
            Text(title, color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = KmpOverlay0, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecentProjectItem(project: ProjectState, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) KmpSurface0 else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("-", color = KmpPurple, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(project.name, color = KmpText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                project.path.takeLast(30).let { if (project.path.length > 30) "...$it" else it },
                color = KmpOverlay0, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SidebarButton(label: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = if (hovered) KmpText else KmpOverlay0,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != PointerEventType.Exit
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun VersionBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(KmpSurface0)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text("Android CLI 2026  |  Compose Multiplatform  |  Kotlin 2.1",
            color = KmpOverlay0, fontSize = 11.sp)
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun NewProjectDialog(onConfirm: (ProjectState) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }

    StudioDialog(title = "New Project", onDismiss = onDismiss) {
        StudioTextField("Project name", name) { name = it }
        Spacer(Modifier.height(12.dp))
        StudioTextField("Directory path", path) { path = it }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmpText)) {
                Text("Cancel")
            }
            Button(
                onClick = { if (name.isNotBlank() && path.isNotBlank()) onConfirm(ProjectState(name, path)) },
                colors  = ButtonDefaults.buttonColors(containerColor = KmpPurple, contentColor = KmpCrust)
            ) { Text("Create") }
        }
    }
}

@Composable
private fun OpenProjectDialog(
    agentBaseUrl: String,
    onConfirm: (ProjectState) -> Unit,
    onDismiss: () -> Unit
) {
    var path        by remember { mutableStateOf("") }
    var showBrowser by remember { mutableStateOf(false) }

    if (showBrowser) {
        FolderBrowserDialog(
            agentBaseUrl = agentBaseUrl,
            onConfirm    = { selected -> path = selected; showBrowser = false },
            onDismiss    = { showBrowser = false }
        )
        return
    }

    StudioDialog(title = "Open Project", onDismiss = onDismiss) {
        // ── Option 1: browse button ───────────────────────────────────────────
        var browseHovered by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (browseHovered) KmpSurface0 else KmpBase)
                .border(1.dp, if (browseHovered) KmpBlue.copy(alpha = 0.6f) else KmpSurface0, RoundedCornerShape(8.dp))
                .clickable { showBrowser = true }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            browseHovered = e.type != PointerEventType.Exit
                        }
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(">", color = KmpBlue, fontSize = 16.sp)
            Column {
                Text("Browse folders", color = KmpText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Navigate your file system visually", color = KmpOverlay0, fontSize = 11.sp)
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = KmpSurface0)
            Text("  or  ", color = KmpOverlay0, fontSize = 11.sp)
            HorizontalDivider(modifier = Modifier.weight(1f), color = KmpSurface0)
        }

        // ── Option 2: direct path ─────────────────────────────────────────────
        StudioTextField(
            label = "Enter path directly",
            value = path,
            placeholder = "/Users/you/projects/my-app",
            onValueChange = { path = it }
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KmpText)) {
                Text("Cancel")
            }
            Button(
                onClick  = { if (path.isNotBlank()) onConfirm(ProjectState(path.trimEnd('/').substringAfterLast("/"), path.trimEnd('/'))) },
                enabled  = path.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = KmpBlue, contentColor = KmpCrust)
            ) { Text("Open") }
        }
    }
}

@Composable
private fun StudioDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KmpMantle)
                .border(1.dp, KmpSurface0, RoundedCornerShape(16.dp))
                .clickable {}  // Absorb click so dialog doesn't close
                .padding(28.dp)
        ) {
            Text(title, color = KmpText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun StudioTextField(
    label: String,
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, color = KmpOverlay0, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            singleLine    = true,
            placeholder   = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = KmpSurface1, fontSize = 13.sp) }
            } else null,
            modifier      = Modifier.fillMaxWidth(),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = KmpPurple,
                unfocusedBorderColor = KmpSurface0,
                focusedTextColor     = KmpText,
                unfocusedTextColor   = KmpText,
                cursorColor          = KmpPurple
            )
        )
    }
}

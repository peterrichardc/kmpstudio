package studio.kmp.frontend.ui.wizard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ui.LogEntry
import studio.kmp.frontend.ui.ProjectState
import studio.kmp.frontend.ws.WsClient
import studio.kmp.frontend.ws.WsState
import studio.kmp.shared.model.WsMessage
import studio.kmp.shared.parser.LineType

private data class WizardState(
    val step: Int = 1,
    val projectName: String = "",
    val parentDir: String = "",
    val packageName: String = "",
    val targets: Set<String> = setOf("android"),
    val architecture: String = "clean",
    val libraries: Set<String> = setOf("coroutines", "serialization", "datetime", "settings"),
    val generating: Boolean = false,
    val progress: List<LogEntry> = emptyList(),
    val done: Boolean = false,
    val aiMode: Boolean = false,
    val aiDescription: String = "",
    val aiThinking: Boolean = false,
    val aiReasoning: String = "",
)

private fun derivePackage(name: String): String {
    val safe = name.lowercase().replace(Regex("[^a-z0-9]"), "")
    return if (safe.isBlank()) "com.example.app" else "com.example.$safe"
}

@Composable
fun NewProjectWizard(
    wsClient: WsClient,
    onComplete: (ProjectState) -> Unit,
    onCancel: () -> Unit
) {
    var s by remember { mutableStateOf(WizardState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(wsClient) {
        wsClient.messages.collect { msg ->
            when (msg) {
                is WsMessage.ScaffoldProgress -> {
                    @Suppress("UNCHECKED_CAST")
                    s = s.copy(
                        progress = s.progress + LogEntry(
                            text     = if (msg.error != null) "Error: ${msg.error}" else "Created: ${msg.filePath}",
                            lineType = if (msg.error != null) LineType.ERROR else LineType.SUCCESS
                        ),
                        done = msg.done
                    )
                    if (msg.done) {
                        onComplete(ProjectState(s.projectName, "${s.parentDir}/${s.projectName}", s.targets.toList()))
                    }
                }
                is WsMessage.AiScaffoldConfig -> {
                    val coreLibs = setOf("coroutines", "serialization", "datetime", "settings")
                    val resolvedLibs = (coreLibs + msg.libraries.toSet()).let { libs ->
                        if (msg.architecture == "library") libs - uiOnlyLibs else libs
                    }
                    s = s.copy(
                        aiThinking   = false,
                        projectName  = msg.projectName.ifBlank { s.projectName },
                        packageName  = msg.packageName,
                        targets      = msg.targets.toSet(),
                        architecture = msg.architecture,
                        libraries    = resolvedLibs,
                        aiReasoning  = msg.reasoning,
                        step         = 5
                    )
                }
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KmpMantle)
                .border(1.dp, KmpSurface0, RoundedCornerShape(16.dp))
        ) {
            WizardHeader(step = s.step, generating = s.generating, aiMode = s.aiMode)

            Box(modifier = Modifier.padding(28.dp).fillMaxWidth()) {
                when {
                    s.generating  -> GeneratingStep(s.progress)
                    s.aiThinking  -> AiThinkingStep()
                    s.step == 1   -> Step1Basics(s,       onChange = { s = it })
                    s.step == 2   -> Step2Targets(s,      onChange = { s = it })
                    s.step == 3   -> Step3Architecture(s, onChange = { s = it })
                    s.step == 4   -> Step4Libraries(s,    onChange = { s = it })
                    s.step == 5   -> Step5Summary(s)
                }
            }

            if (!s.generating && !s.aiThinking) {
                WizardNav(
                    step    = s.step,
                    canNext = stepValid(s),
                    aiMode  = s.aiMode,
                    onBack  = { if (s.step == 1) onCancel() else s = s.copy(step = s.step - 1) },
                    onNext  = {
                        if (s.aiMode && s.step == 1) {
                            s = s.copy(aiThinking = true)
                            scope.launch {
                                if (wsClient.state.value != WsState.CONNECTED) {
                                    wsClient.connect(SettingsStorage.agentPort)
                                    wsClient.state.first { it == WsState.CONNECTED || it == WsState.ERROR }
                                }
                                if (wsClient.state.value == WsState.CONNECTED) {
                                    wsClient.send(WsMessage.AiScaffoldRequest(
                                        description   = s.aiDescription,
                                        projectName   = s.projectName,
                                        parentDir     = s.parentDir,
                                        provider      = SettingsStorage.aiProvider.name,
                                        apiKey        = SettingsStorage.activeApiKey(),
                                        customBaseUrl = SettingsStorage.customBaseUrl.takeIf { it.isNotBlank() }
                                    ))
                                } else {
                                    s = s.copy(aiThinking = false)
                                }
                            }
                        } else if (s.step < 5) {
                            s = s.copy(step = s.step + 1)
                        } else {
                            s = s.copy(generating = true)
                            val req = WsMessage.ScaffoldRequest(
                                projectName  = s.projectName,
                                parentDir    = s.parentDir,
                                packageName  = s.packageName.ifBlank { derivePackage(s.projectName) },
                                targets      = s.targets.toList(),
                                architecture = s.architecture,
                                libraries    = s.libraries.toList()
                            )
                            scope.launch {
                                if (wsClient.state.value != WsState.CONNECTED) {
                                    wsClient.connect(SettingsStorage.agentPort)
                                    wsClient.state.first { it == WsState.CONNECTED || it == WsState.ERROR }
                                }
                                if (wsClient.state.value == WsState.CONNECTED) {
                                    wsClient.send(req)
                                } else {
                                    s = s.copy(
                                        generating = false,
                                        progress   = s.progress + LogEntry(
                                            "Could not connect to agent on port ${SettingsStorage.agentPort}. Is it running?",
                                            LineType.ERROR
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

private fun stepValid(s: WizardState): Boolean = when (s.step) {
    1    -> s.projectName.isNotBlank() && s.parentDir.isNotBlank() &&
            (!s.aiMode || s.aiDescription.isNotBlank())
    2    -> s.targets.isNotEmpty()
    else -> true
}

@Composable
private fun WizardHeader(step: Int, generating: Boolean, aiMode: Boolean = false) {
    val steps = listOf("Basics", "Targets", "Architecture", "Libraries", "Summary")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KmpCrust)
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (generating) "Generating Project..." else "New KMP Project",
                color = KmpText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            if (aiMode && !generating) {
                Text(
                    "AI",
                    color = KmpCrust,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KmpPurple)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, label ->
                val active = i + 1 == step && !generating
                val done   = i + 1 < step || generating
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (done || active) KmpPurple else KmpSurface0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label,
                        color = if (active) KmpPurple else if (done) KmpSubtext else KmpOverlay0,
                        fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun WizardNav(
    step: Int,
    canNext: Boolean,
    aiMode: Boolean = false,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val nextLabel = when {
        step == 5            -> "Generate"
        aiMode && step == 1  -> "Analyze"
        else                 -> "Next"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KmpCrust)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = KmpText)
        ) { Text(if (step == 1) "Cancel" else "< Back") }
        Spacer(Modifier.weight(1f))
        Button(
            onClick  = onNext,
            enabled  = canNext,
            colors   = ButtonDefaults.buttonColors(containerColor = KmpPurple, contentColor = KmpCrust)
        ) { Text(nextLabel) }
    }
}

// ── Steps ─────────────────────────────────────────────────────────────────────

@Composable
private fun Step1Basics(s: WizardState, onChange: (WizardState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Manual",          !s.aiMode) { onChange(s.copy(aiMode = false)) }
            ModeChip("Describe with AI", s.aiMode)  { onChange(s.copy(aiMode = true)) }
        }

        if (s.aiMode) {
            Column {
                Text(
                    "Describe your app",
                    color = KmpOverlay0, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                OutlinedTextField(
                    value         = s.aiDescription,
                    onValueChange = { onChange(s.copy(aiDescription = it)) },
                    placeholder   = {
                        Text(
                            "e.g. A fitness tracker with workout logging, offline support and cloud sync",
                            color = KmpOverlay0, fontSize = 12.sp
                        )
                    },
                    modifier  = Modifier.fillMaxWidth().height(90.dp),
                    maxLines  = 5,
                    colors    = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = KmpPurple,
                        unfocusedBorderColor = KmpSurface0,
                        focusedTextColor     = KmpText,
                        unfocusedTextColor   = KmpText,
                        cursorColor          = KmpPurple
                    )
                )
            }
        }

        WizardField("Project name", "MyKmpApp", s.projectName) { v ->
            onChange(s.copy(
                projectName = v,
                packageName = if (s.packageName.isBlank() || s.packageName == derivePackage(s.projectName))
                    derivePackage(v) else s.packageName
            ))
        }
        WizardField("Parent directory", "/Users/dev", s.parentDir) { onChange(s.copy(parentDir = it)) }
        if (!s.aiMode) {
            WizardField(
                "Package name", "com.example.mykmpapp",
                s.packageName.ifBlank { derivePackage(s.projectName) }
            ) { onChange(s.copy(packageName = it)) }
        }
    }
}

@Composable
private fun Step2Targets(s: WizardState, onChange: (WizardState) -> Unit) {
    val all = listOf(
        "android" to "Android",
        "ios"     to "iOS",
        "desktop" to "Desktop (JVM)",
        "web"     to "Web (Wasm)"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Select compile targets", color = KmpOverlay0, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        all.forEach { (id, label) ->
            CheckRow(label, id in s.targets) { checked ->
                onChange(s.copy(targets = if (checked) s.targets + id else s.targets - id))
            }
        }
    }
}

private val uiOnlyLibs = setOf("coil", "voyager", "molecule")

@Composable
private fun Step3Architecture(s: WizardState, onChange: (WizardState) -> Unit) {
    val options = listOf(
        Triple("library", "Library / SDK",    "No UI — publishable KMP module (AAR + XCFramework)"),
        Triple("clean",   "Clean Architecture", "Domain / Data / Presentation layers with Use Cases"),
        Triple("mvi",     "MVI",                "State + Intent + Effect sealed classes with a Store"),
        Triple("mvvm",    "MVVM",               "ViewModel with StateFlow, minimal boilerplate")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (id, title, desc) ->
            ArchRow(id, title, desc, s.architecture == id) {
                val updated = s.copy(architecture = id)
                val sanitized = if (id == "library") updated.copy(
                    libraries = updated.libraries - uiOnlyLibs
                ) else updated
                onChange(sanitized)
            }
        }
    }
}

@Composable
private fun Step4Libraries(s: WizardState, onChange: (WizardState) -> Unit) {
    val isLibrary = s.architecture == "library"
    val groups = listOf(
        "Core (always included)" to listOf(
            "coroutines"    to "Kotlin Coroutines",
            "serialization" to "Kotlin Serialization",
            "datetime"      to "kotlinx-datetime",
            "settings"      to "Multiplatform Settings"
        ),
        "Network + Data" to listOf(
            "ktor"       to "Ktor Client",
            "sqldelight" to "SQLDelight",
            "datastore"  to "DataStore"
        ),
        "DI + UI" to listOf(
            "koin"     to "Koin",
            "coil"     to "Coil (image loading)",
            "voyager"  to "Voyager (navigation)",
            "molecule" to "Molecule"
        )
    )
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { (groupName, libs) ->
            Text(groupName, color = KmpPurple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            libs.forEach { (id, label) ->
                val core = groupName.startsWith("Core")
                val uiOnly = isLibrary && id in uiOnlyLibs
                val displayLabel = if (uiOnly) "$label (N/A for libraries)" else label
                CheckRow(displayLabel, core || id in s.libraries, enabled = !core && !uiOnly) { checked ->
                    onChange(s.copy(libraries = if (checked) s.libraries + id else s.libraries - id))
                }
            }
        }
    }
}

@Composable
private fun Step5Summary(s: WizardState) {
    val lines = listOf(
        "Name"         to s.projectName,
        "Directory"    to "${s.parentDir}/${s.projectName}",
        "Package"      to s.packageName.ifBlank { derivePackage(s.projectName) },
        "Targets"      to s.targets.joinToString(", "),
        "Architecture" to if (s.architecture == "library") "Library / SDK" else s.architecture.replaceFirstChar { it.uppercase() },
        "Libraries"    to s.libraries.joinToString(", ").ifBlank { "—" }
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.aiReasoning.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(KmpSurface0)
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(s.aiReasoning, color = KmpSubtext, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
        Text("Review before generating", color = KmpOverlay0, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        lines.forEach { (key, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(key,   color = KmpOverlay0, fontSize = 12.sp, modifier = Modifier.width(100.dp))
                Text(value, color = KmpText,     fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GeneratingStep(progress: List<LogEntry>) {
    val scroll = rememberScrollState()
    LaunchedEffect(progress.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KmpCrust)
            .padding(12.dp)
            .verticalScroll(scroll)
    ) {
        if (progress.isEmpty()) {
            Text("Starting...", color = KmpOverlay0, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        progress.forEach { entry ->
            Text(
                text       = entry.text,
                color      = if (entry.lineType == LineType.ERROR) KmpRed else KmpGreen,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun AiThinkingStep() {
    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = KmpPurple, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text("Analyzing your description...", color = KmpText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text("Choosing targets, architecture & libraries", color = KmpOverlay0, fontSize = 12.sp)
    }
}

// ── Reusable widgets ──────────────────────────────────────────────────────────

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color      = if (selected) KmpCrust else KmpSubtext,
        fontSize   = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier   = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) KmpPurple else KmpSurface0)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun WizardField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = KmpOverlay0, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            placeholder = { Text(hint, color = KmpOverlay0, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = KmpPurple,
                unfocusedBorderColor = KmpSurface0,
                focusedTextColor     = KmpText,
                unfocusedTextColor   = KmpText,
                cursorColor          = KmpPurple
            )
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked, onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors  = CheckboxDefaults.colors(
                checkedColor   = KmpPurple,
                checkmarkColor = KmpCrust
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (enabled) KmpText else KmpOverlay0, fontSize = 13.sp)
    }
}

@Composable
private fun ArchRow(id: String, title: String, desc: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) KmpSurface0 else Color.Transparent)
            .border(1.dp, if (selected) KmpPurple.copy(alpha = 0.5f) else KmpSurface0, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = onSelect,
            colors   = RadioButtonDefaults.colors(selectedColor = KmpPurple, unselectedColor = KmpOverlay0)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = KmpText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc,  color = KmpOverlay0, fontSize = 11.sp)
        }
    }
}

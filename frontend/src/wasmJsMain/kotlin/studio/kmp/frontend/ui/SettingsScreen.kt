package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import studio.kmp.frontend.interop.checkAgentHealth
import studio.kmp.frontend.interop.copyToClipboard
import studio.kmp.frontend.interop.getAgentHealth
import studio.kmp.frontend.interop.getAiTestResult
import studio.kmp.frontend.interop.testAiKey
import studio.kmp.frontend.storage.AiProvider
import studio.kmp.frontend.storage.SettingsStorage
import studio.kmp.frontend.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Mirror storage into local state so Save is explicit
    var provider     by remember { mutableStateOf(SettingsStorage.aiProvider) }
    var claudeKey    by remember { mutableStateOf(SettingsStorage.claudeApiKey) }
    var openAiKey    by remember { mutableStateOf(SettingsStorage.openAiApiKey) }
    var geminiKey    by remember { mutableStateOf(SettingsStorage.geminiApiKey) }
    var customUrl    by remember { mutableStateOf(SettingsStorage.customBaseUrl) }
    var customKey    by remember { mutableStateOf(SettingsStorage.customApiKey) }
    var agentPort    by remember { mutableStateOf(SettingsStorage.agentPort.toString()) }
    var savedBanner  by remember { mutableStateOf(false) }

    var globalTestRun    by remember { mutableStateOf(0) }
    var globalTestStatus by remember { mutableStateOf(TestStatus.IDLE) }
    var globalTestError  by remember { mutableStateOf("") }

    LaunchedEffect(provider) { globalTestStatus = TestStatus.IDLE; globalTestError = "" }

    LaunchedEffect(globalTestRun) {
        if (globalTestRun == 0) return@LaunchedEffect
        globalTestStatus = TestStatus.LOADING
        val activeKey = when (provider) {
            AiProvider.CLAUDE -> claudeKey
            AiProvider.OPENAI -> openAiKey
            AiProvider.GEMINI -> geminiKey
            AiProvider.CUSTOM -> customKey
        }
        val port = agentPort.toIntOrNull() ?: 8765
        testAiKey("http://127.0.0.1:$port", provider.name, activeKey, customUrl)
        var result: String? = null
        while (result == null) { delay(100); result = getAiTestResult() }
        if (result == "ok") {
            globalTestStatus = TestStatus.OK
        } else {
            globalTestStatus = TestStatus.ERROR
            globalTestError  = result.removePrefix("error:")
        }
    }

    fun save() {
        SettingsStorage.aiProvider    = provider
        SettingsStorage.claudeApiKey  = claudeKey
        SettingsStorage.openAiApiKey  = openAiKey
        SettingsStorage.geminiApiKey  = geminiKey
        SettingsStorage.customBaseUrl = customUrl
        SettingsStorage.customApiKey  = customKey
        SettingsStorage.agentPort     = agentPort.toIntOrNull() ?: 8765
        savedBanner = true
    }

    Box(modifier = Modifier.fillMaxSize().background(KmpBase)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(KmpMantle)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    colors  = ButtonDefaults.textButtonColors(contentColor = KmpBlue)
                ) {
                    Text("< Back", fontSize = 13.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text("Settings", color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (savedBanner) {
                    Text("Saved", color = KmpGreen, fontSize = 13.sp)
                    Spacer(Modifier.width(16.dp))
                }
                Button(
                    onClick = ::save,
                    colors  = ButtonDefaults.buttonColors(containerColor = KmpPurple, contentColor = KmpCrust)
                ) { Text("Save") }
            }

            // ── Scrollable body ─────────────────────────────────────────────
            val scroll = rememberScrollState()
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // ── AI Provider ─────────────────────────────────────────
                    SettingsSection(title = "AI Provider") {
                        AiProvider.entries.forEach { p ->
                            ProviderRadioRow(
                                provider   = p,
                                selected   = provider == p,
                                onSelect   = { provider = p; savedBanner = false }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = KmpSurface0)
                        Spacer(Modifier.height(12.dp))
                        TestConnectionRow(
                            providerName = provider.displayName,
                            status       = globalTestStatus,
                            error        = globalTestError,
                            onTest       = { globalTestRun++; globalTestStatus = TestStatus.IDLE; globalTestError = "" }
                        )
                    }

                    // ── API Keys ─────────────────────────────────────────────
                    SettingsSection(title = "API Keys") {
                        val port = agentPort.toIntOrNull() ?: 8765
                        ApiKeyField(
                            label        = "Claude (Anthropic)",
                            hint         = "sk-ant-...",
                            value        = claudeKey,
                            highlighted  = provider == AiProvider.CLAUDE,
                            agentPort    = port,
                            providerName = "CLAUDE",
                            onChange     = { claudeKey = it; savedBanner = false }
                        )
                        Spacer(Modifier.height(12.dp))
                        ApiKeyField(
                            label        = "OpenAI",
                            hint         = "sk-...",
                            value        = openAiKey,
                            highlighted  = provider == AiProvider.OPENAI,
                            agentPort    = port,
                            providerName = "OPENAI",
                            onChange     = { openAiKey = it; savedBanner = false }
                        )
                        Spacer(Modifier.height(12.dp))
                        ApiKeyField(
                            label        = "Google Gemini",
                            hint         = "AIza...",
                            value        = geminiKey,
                            highlighted  = provider == AiProvider.GEMINI,
                            agentPort    = port,
                            providerName = "GEMINI",
                            onChange     = { geminiKey = it; savedBanner = false }
                        )
                    }

                    // ── Custom provider (OpenAI-compatible) ───────────────────
                    SettingsSection(title = "Custom OpenAI-compatible Endpoint") {
                        SettingRow(label = "Base URL", hint = "https://api.example.com/v1",
                            value = customUrl, highlighted = provider == AiProvider.CUSTOM) {
                            customUrl = it; savedBanner = false
                        }
                        Spacer(Modifier.height(12.dp))
                        ApiKeyField(
                            label         = "API Key",
                            hint          = "...",
                            value         = customKey,
                            highlighted   = provider == AiProvider.CUSTOM,
                            agentPort     = agentPort.toIntOrNull() ?: 8765,
                            providerName  = "CUSTOM",
                            customBaseUrl = customUrl,
                            onChange      = { customKey = it; savedBanner = false }
                        )
                    }

                    // ── Local agent ───────────────────────────────────────────
                    SettingsSection(title = "Local Agent") {
                        SettingRow(label = "WebSocket port", hint = "8765",
                            value = agentPort, highlighted = false) {
                            agentPort = it; savedBanner = false
                        }
                        Spacer(Modifier.height(16.dp))
                        AgentStatusRow(port = agentPort.toIntOrNull() ?: 8765)
                    }
                }
                VerticalScrollbar(
                    adapter  = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TestConnectionRow(
    providerName: String,
    status: TestStatus,
    error: String,
    onTest: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Test connection",
                color = KmpSubtext, fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            when (status) {
                TestStatus.IDLE -> Button(
                    onClick = onTest,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = KmpSurface0,
                        contentColor   = KmpText
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Test $providerName", fontSize = 12.sp)
                }
                TestStatus.LOADING -> Text(
                    "Testing...",
                    color = KmpOverlay0, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                TestStatus.OK -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connected", color = KmpGreen, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = onTest,
                        colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
                    ) { Text("Re-test", fontSize = 11.sp) }
                }
                TestStatus.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Failed", color = KmpRed, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = onTest,
                        colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
                    ) { Text("Retry", fontSize = 11.sp) }
                }
            }
        }
        if (status == TestStatus.ERROR && error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                error,
                color = KmpRed, fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KmpMantle)
            .border(1.dp, KmpSurface0, RoundedCornerShape(12.dp))
            .padding(24.dp)
    ) {
        Text(title, color = KmpPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

@Composable
private fun ProviderRadioRow(provider: AiProvider, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) KmpSurface0 else KmpMantle)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick  = onSelect,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = KmpPurple,
                unselectedColor = KmpOverlay0
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(provider.displayName, color = if (selected) KmpText else KmpSubtext, fontSize = 14.sp)
        if (selected) {
            Spacer(Modifier.weight(1f))
            Text("Active", color = KmpGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private enum class TestStatus { IDLE, LOADING, OK, ERROR }

@Composable
private fun ApiKeyField(
    label: String,
    hint: String,
    value: String,
    highlighted: Boolean,
    agentPort: Int,
    providerName: String,
    customBaseUrl: String = "",
    onChange: (String) -> Unit
) {
    var visible    by remember { mutableStateOf(false) }
    var testRun    by remember { mutableStateOf(0) }
    var testStatus by remember { mutableStateOf(TestStatus.IDLE) }
    var testError  by remember { mutableStateOf("") }

    LaunchedEffect(testRun) {
        if (testRun == 0) return@LaunchedEffect
        testStatus = TestStatus.LOADING
        testAiKey("http://127.0.0.1:$agentPort", providerName, value, customBaseUrl)
        var result: String? = null
        while (result == null) { delay(100); result = getAiTestResult() }
        if (result == "ok") {
            testStatus = TestStatus.OK
        } else {
            testStatus = TestStatus.ERROR
            testError  = result.removePrefix("error:")
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (highlighted) KmpPurple else KmpOverlay0,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            if (highlighted) {
                Spacer(Modifier.width(6.dp))
                ActiveBadge()
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value                = value,
            onValueChange        = { onChange(it); testStatus = TestStatus.IDLE },
            singleLine           = true,
            placeholder          = { Text(hint, color = KmpOverlay0, fontSize = 13.sp) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotBlank()) {
                        when (testStatus) {
                            TestStatus.IDLE -> TextButton(
                                onClick = { testRun++ },
                                colors  = ButtonDefaults.textButtonColors(contentColor = KmpBlue)
                            ) { Text("Test", fontSize = 11.sp) }
                            TestStatus.LOADING -> Text(
                                "Testing...",
                                color = KmpOverlay0, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            TestStatus.OK -> Text(
                                "Valid",
                                color = KmpGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            TestStatus.ERROR -> TextButton(
                                onClick = { testRun++ },
                                colors  = ButtonDefaults.textButtonColors(contentColor = KmpRed)
                            ) { Text("Retry", fontSize = 11.sp) }
                        }
                    }
                    TextButton(
                        onClick = { visible = !visible },
                        colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
                    ) { Text(if (visible) "Hide" else "Show", fontSize = 11.sp) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = if (highlighted) KmpPurple else KmpBlue,
                unfocusedBorderColor = if (highlighted) KmpPurple.copy(alpha = 0.4f) else KmpSurface0,
                focusedTextColor     = KmpText,
                unfocusedTextColor   = KmpText,
                cursorColor          = KmpPurple
            )
        )
        if (testStatus == TestStatus.ERROR && testError.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(testError, color = KmpRed, fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    hint: String,
    value: String,
    highlighted: Boolean,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = if (highlighted) KmpPurple else KmpOverlay0, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            singleLine    = true,
            placeholder   = { Text(hint, color = KmpOverlay0, fontSize = 13.sp) },
            modifier      = Modifier.fillMaxWidth(),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = KmpBlue,
                unfocusedBorderColor = KmpSurface0,
                focusedTextColor     = KmpText,
                unfocusedTextColor   = KmpText,
                cursorColor          = KmpPurple
            )
        )
    }
}

@Composable
private fun AgentStatusRow(port: Int) {
    var healthy by remember { mutableStateOf(false) }
    var copied  by remember { mutableStateOf(false) }
    val command = "./gradlew :agent:run"

    // Poll health every 3 seconds
    LaunchedEffect(port) {
        while (true) {
            checkAgentHealth("http://127.0.0.1:$port/health")
            delay(500) // let fetch resolve
            healthy = getAgentHealth()
            delay(2500)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (healthy) KmpGreen else KmpOverlay0)
        )
        Spacer(Modifier.width(10.dp))

        if (healthy) {
            Text("Agent running on port $port", color = KmpGreen,
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            // Clickable row that copies the start command
            var hovered by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hovered) KmpSurface0 else KmpMantle)
                    .clickable {
                        copyToClipboard(command)
                        copied = true
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                hovered = e.type != PointerEventType.Exit
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Agent offline", color = KmpRed,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (copied) "Copied!" else "Click to copy start command",
                    color = if (copied) KmpGreen else KmpOverlay0,
                    fontSize = 11.sp
                )
            }

            // Reset "Copied!" label after 2s
            if (copied) {
                LaunchedEffect(copied) {
                    delay(2000)
                    copied = false
                }
            }
        }
    }

    if (!healthy) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Run in your terminal:  $command",
            color = KmpOverlay0, fontSize = 11.sp
        )
    }
}

@Composable
private fun ActiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(KmpPurple.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) { Text("active", color = KmpPurple, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

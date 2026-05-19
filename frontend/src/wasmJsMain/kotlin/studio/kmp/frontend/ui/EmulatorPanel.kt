package studio.kmp.frontend.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import studio.kmp.frontend.theme.*
import studio.kmp.frontend.ws.WsState
import studio.kmp.shared.model.AvdInfo
import studio.kmp.shared.model.WsMessage

@Composable
fun EmulatorPanel(
    wsState:          WsState,
    agentPort:        Int,
    avds:             List<AvdInfo>,
    emulatorStatus:   WsMessage.EmulatorStatus?,
    activeSerial:     String?,
    projectPath:      String? = null,
    showNewDialog:    Boolean,
    onNewDialogChange: (Boolean) -> Unit,
    onStartEmulator:  (avdName: String) -> Unit,
    onStopEmulator:   (serial: String) -> Unit,
    onCreateAvd:      (name: String) -> Unit,
    onConnectDevice:  (serial: String) -> Unit,
    onRefresh:        () -> Unit,
    modifier:         Modifier = Modifier
) {
    val isStarting  = emulatorStatus?.phase?.let { it == "starting" || it == "booting" } ?: false
    val bootMsg     = emulatorStatus?.message

    val runningDevices  = avds.filter { it.running && it.serial != null }
    val availableAvds   = avds.filter { !it.running }

    // selected AVD to start (non-running)
    var selectedAvd by remember { mutableStateOf(availableAvds.firstOrNull()?.name) }
    LaunchedEffect(availableAvds) {
        if (selectedAvd == null || availableAvds.none { it.name == selectedAvd })
            selectedAvd = availableAvds.firstOrNull()?.name
    }
    // selected running device to connect to
    var selectedDevice by remember { mutableStateOf(activeSerial ?: runningDevices.firstOrNull()?.serial) }
    LaunchedEffect(runningDevices, activeSerial) {
        if (selectedDevice == null || runningDevices.none { it.serial == selectedDevice })
            selectedDevice = activeSerial ?: runningDevices.firstOrNull()?.serial
    }

    var newAvdName by remember { mutableStateOf("") }

    // ── New AVD dialog ─────────────────────────────────────────────────────────
    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { onNewDialogChange(false); newAvdName = "" },
            containerColor   = KmpCrust,
            shape            = RoundedCornerShape(12.dp),
            title = { Text("New AVD", color = KmpText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AVD name", color = KmpSubtext, fontSize = 12.sp)
                    OutlinedTextField(
                        value         = newAvdName,
                        onValueChange = { newAvdName = it },
                        singleLine    = true,
                        placeholder   = { Text("e.g. Pixel_8_API34", color = KmpOverlay0, fontSize = 13.sp) },
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = KmpBlue,
                            unfocusedBorderColor = KmpSurface0,
                            focusedTextColor     = KmpText,
                            unfocusedTextColor   = KmpText,
                            cursorColor          = KmpBlue
                        )
                    )
                    Text(
                        "Uses the first available system image. Install images via Android Studio SDK Manager.",
                        color    = KmpOverlay0,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onNewDialogChange(false); newAvdName = "" },
                    colors  = ButtonDefaults.textButtonColors(contentColor = KmpOverlay0)
                ) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick  = {
                        if (newAvdName.isNotBlank()) {
                            onCreateAvd(newAvdName.trim())
                            onNewDialogChange(false)
                            newAvdName = ""
                        }
                    },
                    enabled = newAvdName.isNotBlank(),
                    colors  = ButtonDefaults.buttonColors(containerColor = KmpBlue, contentColor = KmpCrust)
                ) { Text("Create", fontWeight = FontWeight.SemiBold) }
            }
        )
    }

    Column(modifier = modifier.background(KmpBase)) {

        // ── Top controls bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KmpMantle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status dot
            val (dotColor, statusLabel) = emulatorStatusLabel(emulatorStatus, activeSerial, isStarting)
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dotColor))
            Text(statusLabel, color = dotColor, fontSize = 11.sp,
                modifier = Modifier.widthIn(max = 80.dp), maxLines = 1)

            if (isStarting && bootMsg != null) {
                Text(bootMsg, color = KmpSubtext, fontSize = 10.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Spacer(Modifier.weight(1f))
            }

            // New button
            EmuChip("New", KmpPurple) { onNewDialogChange(true) }

            // Refresh button
            EmuChip("Refresh", KmpOverlay0) { onRefresh() }
        }

        // ── Device / AVD selector row ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KmpCrust)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (runningDevices.isNotEmpty()) {
                // Running device picker + Stop / Connect
                Text("Device:", color = KmpOverlay0, fontSize = 10.sp)
                DeviceDropdown(
                    items    = runningDevices,
                    selected = selectedDevice,
                    onSelect = { selectedDevice = it }
                )
                // Connect to selected device (sets active serial for video)
                val isConnected = selectedDevice == activeSerial
                EmuChip(
                    if (isConnected) "Connected" else "Connect",
                    if (isConnected) KmpGreen else KmpBlue,
                    enabled = !isConnected && selectedDevice != null
                ) {
                    selectedDevice?.let { onConnectDevice(it) }
                }
                // Stop selected device
                EmuChip("Stop", KmpRed, enabled = selectedDevice != null) {
                    selectedDevice?.let { onStopEmulator(it) }
                }
            }

            if (availableAvds.isNotEmpty()) {
                if (runningDevices.isNotEmpty()) {
                    Box(modifier = Modifier.width(1.dp).height(18.dp).background(KmpSurface0))
                }
                // Available AVD picker + Start
                Text("AVD:", color = KmpOverlay0, fontSize = 10.sp)
                AvdDropdown(
                    items    = availableAvds,
                    selected = selectedAvd,
                    onSelect = { selectedAvd = it }
                )
                EmuChip("Start", KmpGreen, enabled = selectedAvd != null && !isStarting) {
                    selectedAvd?.let { onStartEmulator(it) }
                }
            }

            if (avds.isEmpty() && !isStarting) {
                Text("No AVDs found. Create one with New.", color = KmpOverlay0, fontSize = 10.sp)
            }
        }

        // ── Video canvas ───────────────────────────────────────────────────────
        EmulatorCanvas(
            wsState   = wsState,
            agentPort = agentPort,
            deviceId  = activeSerial,
            workDir   = projectPath,
            modifier  = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun emulatorStatusLabel(
    status: WsMessage.EmulatorStatus?,
    activeSerial: String?,
    isStarting: Boolean
): Pair<Color, String> = when {
    isStarting          -> KmpYellow to (if (status?.phase == "booting") "Booting..." else "Starting...")
    activeSerial != null -> KmpGreen  to activeSerial
    status?.phase == "error"   -> KmpRed    to "Error"
    status?.phase == "stopped" -> KmpOverlay0 to "Stopped"
    else                -> KmpOverlay0 to "Offline"
}

@Composable
private fun DeviceDropdown(items: List<AvdInfo>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = items.firstOrNull { it.serial == selected }?.let { "${it.name} (${it.serial})" }
        ?: selected ?: items.firstOrNull()?.serial ?: "—"
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(KmpSurface0)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(label, color = KmpText, fontSize = 10.sp, maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp), overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.background(KmpCrust)) {
            items.forEach { avd ->
                val itemLabel = "${avd.name}${avd.serial?.let { " ($it)" } ?: ""}"
                DropdownMenuItem(
                    text    = { Text(itemLabel, color = KmpText, fontSize = 12.sp) },
                    onClick = { avd.serial?.let { onSelect(it) }; expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AvdDropdown(items: List<AvdInfo>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(KmpSurface0)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(selected ?: items.firstOrNull()?.name ?: "—", color = KmpText, fontSize = 10.sp,
                maxLines = 1, modifier = Modifier.widthIn(max = 140.dp), overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.background(KmpCrust)) {
            items.forEach { avd ->
                DropdownMenuItem(
                    text    = { Text(avd.name, color = KmpText, fontSize = 12.sp) },
                    onClick = { onSelect(avd.name); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EmuChip(
    label:   String,
    color:   Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) color.copy(alpha = 0.15f) else KmpSurface0.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = if (enabled) color else KmpOverlay0,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

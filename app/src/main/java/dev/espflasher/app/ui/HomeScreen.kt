package dev.espflasher.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.FlashPhase
import dev.espflasher.app.domain.FlashStateMachine
import dev.espflasher.app.ui.i18n.LocalUiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FlasherViewModel,
    onPickFirmware: (Uri) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val t = LocalUiText.current
    val view = LocalView.current
    val keepAwake = state.keepAwake && FlashStateMachine.isFlashing(state.phase)
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickFirmware(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Column {
                        Text(t.appName, fontWeight = FontWeight.SemiBold)
                        Text(
                            t.homeIntro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = t.settings)
                    }
                },
            )
        },
    ) { padding ->
        when {
            FlashStateMachine.isFlashing(state.phase) -> FlashProgressPane(state, vm, padding)
            state.phase == FlashPhase.Success && state.result != null -> SuccessPane(state, vm, padding)
            else -> HomePane(state, vm, padding, onPick = { pick.launch(arrayOf("application/octet-stream", "*/*")) })
        }
    }
    if (state.mismatchOpen) {
        AlertDialog(
            onDismissRequest = vm::cancelMismatch,
            title = { Text(t.mismatchTitle) },
            text = { Text(state.compatibility?.message ?: t.mismatchBody) },
            confirmButton = {
                TextButton(onClick = vm::confirmMismatch) { Text(t.flashAnyway) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelMismatch) { Text(t.cancel) }
            },
        )
    }
}

@Composable
private fun HomePane(
    state: UiState,
    vm: FlasherViewModel,
    padding: PaddingValues,
    onPick: () -> Unit,
) {
    val t = LocalUiText.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { err ->
            if (!state.needBootButton) {
                ElevatedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(t.errorTitle(err.code), style = MaterialTheme.typography.titleMedium)
                        Text(
                            t.errorBody(err.code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { vm.connectAttachedOrPrompt() }) {
                            Text(t.errorAction(err.code))
                        }
                    }
                }
            }
        }
        if (state.needBootButton) {
            ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Text(t.enterBootloader, style = MaterialTheme.typography.titleMedium)
                    Text(t.holdBoot, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { vm.connectAttachedOrPrompt() }) { Text(t.tryAgain) }
                }
            }
        }
        DeviceCard(state, vm)
        FirmwareCard(state, vm, onPick)
        OptionsCard(state, vm)
        Button(
            onClick = vm::requestFlash,
            enabled = state.device != null && state.images.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
        ) { Text(t.flash) }
        LogsCard(state)
    }
}

@Composable
private fun DeviceCard(state: UiState, vm: FlasherViewModel) {
    val t = LocalUiText.current
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            val device = state.device
            if (device == null) {
                Icon(Icons.Outlined.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(t.noDevice, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(t.plugInHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.connectAttachedOrPrompt() }, shape = CircleShape) {
                    Icon(Icons.Outlined.Usb, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t.connectBoard)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(device.chipId.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(t.connected) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("${t.chip}  ${device.description}")
                Text("${t.flashSize}  ${device.flashSizeLabel}")
                Text("${t.usb}  ${device.usbLabel}")
                Text(
                    if (device.bootloaderReady) t.bootloaderReady else t.bootloaderNotReady,
                )
                device.macAddress?.let { Text("${t.mac}  $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun FirmwareCard(state: UiState, vm: FlasherViewModel, onPick: () -> Unit) {
    val t = LocalUiText.current
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.firmware, style = MaterialTheme.typography.titleMedium)
            if (state.images.isEmpty()) {
                Text(t.selectFirmware, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onPick, shape = CircleShape) { Text(t.chooseFirmware) }
            } else {
                state.images.forEach { img ->
                    Text(img.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${img.size / 1024} KB · 0x${img.address.toString(16).uppercase()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                state.compatibility?.takeIf { it.verdict.name == "MISMATCH" }?.let {
                    Text(t.mismatchBanner, color = MaterialTheme.colorScheme.error)
                    Text(it.message, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPick) { Text(t.change) }
                    TextButton(onClick = vm::removeFirmware) { Text(t.remove) }
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(state: UiState, vm: FlasherViewModel) {
    val t = LocalUiText.current
    var advanced by remember { mutableStateOf(false) }
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.flashOptions, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t.eraseBefore)
                Switch(checked = state.config.eraseBeforeFlash, onCheckedChange = { v -> vm.updateConfig { it.copy(eraseBeforeFlash = v) } })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t.verifyAfter)
                Switch(checked = state.config.verifyAfterFlash, onCheckedChange = { v -> vm.updateConfig { it.copy(verifyAfterFlash = v) } })
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) t.hideAdvanced else t.advanced)
            }
            if (advanced) {
                Text(t.chipOverride, style = MaterialTheme.typography.labelMedium)
                ChipId.entries.forEach { id ->
                    FilterChip(
                        selected = state.config.chipOverride == id,
                        onClick = { vm.updateConfig { it.copy(chipOverride = id) } },
                        label = { Text(id.label) },
                    )
                }
                FilterChip(
                    selected = state.config.chipOverride == null,
                    onClick = { vm.updateConfig { it.copy(chipOverride = null) } },
                    label = { Text(t.automatic) },
                )
            }
        }
    }
}

@Composable
private fun LogsCard(state: UiState) {
    val t = LocalUiText.current
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.logs, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { open = !open }) { Text(if (open) t.hide else t.show) }
            }
            if (open) {
                Text(
                    state.logs.joinToString("\n").ifEmpty { t.noLogs },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 220.dp),
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.logs.joinToString("\n"))) }) {
                    Text(t.copyLogs)
                }
            }
        }
    }
}

@Composable
private fun FlashProgressPane(state: UiState, vm: FlasherViewModel, padding: PaddingValues) {
    val t = LocalUiText.current
    val pct = (state.progress?.percent ?: 0f) / 100f
    val animated by animateFloatAsState(pct, label = "flash")
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${t.flashing} ${state.device?.chipId?.label ?: "ESP"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        val color = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceVariant
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Canvas(Modifier.size(180.dp)) {
                drawArc(track, -90f, 360f, false, style = Stroke(18f, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * animated, false, style = Stroke(18f, cap = StrokeCap.Round))
            }
            Text("${(animated * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text(t.phaseTitle(state.phase), style = MaterialTheme.typography.titleMedium)
        state.progress?.let {
            Text("${it.written / 1024} KB / ${it.total / 1024} KB · ${"%.1f".format(it.bytesPerSec / 1024)} KB/s", fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = vm::cancelFlash, shape = CircleShape) { Text(t.cancel) }
    }
}

@Composable
private fun SuccessPane(state: UiState, vm: FlasherViewModel, padding: PaddingValues) {
    val t = LocalUiText.current
    val result = state.result ?: return
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(t.flashComplete, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(t.flashedOk.format(result.chipLabel))
        Spacer(Modifier.height(16.dp))
        Text(result.firmwareName)
        Text("${result.size / 1024} KB · ${result.durationMs / 1000}s")
        Text(if (result.verified) t.verificationPassed else t.verificationSkipped)
        Spacer(Modifier.height(24.dp))
        Button(onClick = vm::resetToReady, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(t.flashAgain) }
        FilledTonalButton(onClick = vm::resetToReady, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(t.done) }
    }
}

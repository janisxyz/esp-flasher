package dev.espflasher.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: FlasherViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    EspFlasherTheme(theme = state.theme, dynamicColor = true) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("General", style = MaterialTheme.typography.titleMedium)
                        Text("Theme")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system", "light", "dark").forEach { t ->
                                FilterChip(selected = state.theme == t, onClick = { vm.setTheme(t) }, label = { Text(t.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Keep screen awake during flashing")
                            Switch(checked = state.keepAwake, onCheckedChange = { vm.setKeepAwake(it) })
                        }
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Flashing", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Verify after flash")
                            Switch(checked = state.config.verifyAfterFlash, onCheckedChange = { v -> vm.updateConfig { it.copy(verifyAfterFlash = v) } })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Erase before flash")
                            Switch(checked = state.config.eraseBeforeFlash, onCheckedChange = { v -> vm.updateConfig { it.copy(eraseBeforeFlash = v) } })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Automatic bootloader entry")
                            Switch(checked = state.config.autoBootloader, onCheckedChange = { v -> vm.updateConfig { it.copy(autoBootloader = v) } })
                        }
                    }
                }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://janisxyz.github.io/esp-flasher/")),
                        )
                    },
                ) {
                    Text("Privacy policy")
                }
            }
        }
    }
}

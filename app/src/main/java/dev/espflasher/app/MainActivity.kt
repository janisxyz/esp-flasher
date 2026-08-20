package dev.espflasher.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.espflasher.app.ui.FlasherViewModel
import dev.espflasher.app.ui.HomeScreen
import dev.espflasher.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: FlasherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIncoming()
        setContent {
            var settings by remember { mutableStateOf(false) }
            if (settings) {
                SettingsScreen(vm) { settings = false }
            } else {
                HomeScreen(
                    vm = vm,
                    onPickFirmware = { uri -> loadUri(uri) },
                    onOpenSettings = { settings = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncoming()
    }

    private fun consumeIncoming() {
        vm.consumeShareIntent(intent) { uri -> readUri(uri) }
    }

    private fun loadUri(uri: Uri) {
        val file = readUri(uri) ?: return
        vm.addFirmware(uri, file.first, file.second)
    }

    private fun readUri(uri: Uri): Pair<String, ByteArray>? {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else "firmware.bin"
        } ?: "firmware.bin"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return name to bytes
    }
}

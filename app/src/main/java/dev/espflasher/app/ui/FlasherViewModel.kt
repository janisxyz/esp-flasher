package dev.espflasher.app.ui

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.espflasher.app.domain.AppError
import dev.espflasher.app.domain.AppErrorCode
import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.Compatibility
import dev.espflasher.app.domain.DetectedDevice
import dev.espflasher.app.domain.FirmwareImage
import dev.espflasher.app.domain.FirmwareInspection
import dev.espflasher.app.domain.FlashConfig
import dev.espflasher.app.domain.FlashPhase
import dev.espflasher.app.domain.FlashProgress
import dev.espflasher.app.domain.FlashResult
import dev.espflasher.app.domain.FlashStateMachine
import dev.espflasher.app.domain.FirmwareInspector
import dev.espflasher.app.flash.FlashingRepository
import dev.espflasher.app.usb.UsbEvent
import dev.espflasher.app.usb.UsbMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val phase: FlashPhase = FlashPhase.Disconnected,
    val device: DetectedDevice? = null,
    val images: List<FirmwareImage> = emptyList(),
    val inspections: List<FirmwareInspection> = emptyList(),
    val logs: List<String> = emptyList(),
    val progress: FlashProgress? = null,
    val error: AppError? = null,
    val result: FlashResult? = null,
    val needBootButton: Boolean = false,
    val mismatchOpen: Boolean = false,
    val config: FlashConfig = FlashConfig(),
    val theme: String = "system",
    val keepAwake: Boolean = true,
    val debugLogging: Boolean = false,
) {
    val compatibility: Compatibility?
        get() = inspections.firstOrNull()?.let {
            FirmwareInspector.compatibility(it, config.chipOverride ?: device?.chipId)
        }
}

class FlasherViewModel(app: Application) : AndroidViewModel(app) {
    private val monitor = UsbMonitor(app)
    private val repo = FlashingRepository()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var flashJob: Job? = null
    private var pendingDevice: UsbDevice? = null

    init {
        viewModelScope.launch {
            monitor.events.collect { event ->
                when (event) {
                    is UsbEvent.Attached -> {
                        log("USB device connected")
                        pendingDevice = event.device
                        if (monitor.hasPermission(event.device)) connect(event.device)
                        else monitor.requestPermission(event.device)
                    }
                    is UsbEvent.Permission -> {
                        log(if (event.granted) "USB permission granted" else "USB permission denied")
                        if (event.granted) connect(event.device)
                        else setError(AppError.of(AppErrorCode.USB_PERMISSION))
                    }
                    is UsbEvent.Detached -> {
                        log("USB device removed")
                        viewModelScope.launch { repo.disconnect() }
                        _state.update {
                            it.copy(
                                device = null,
                                phase = FlashPhase.Disconnected,
                                progress = null,
                                error = if (FlashStateMachine.isFlashing(it.phase))
                                    AppError.of(AppErrorCode.DISCONNECT) else it.error,
                            )
                        }
                    }
                }
            }
        }
    }

    fun connectAttachedOrPrompt() {
        val existing = monitor.listSerialDevices().firstOrNull()
        if (existing == null) {
            setError(AppError.of(AppErrorCode.NO_DEVICE))
            return
        }
        if (monitor.hasPermission(existing)) connect(existing)
        else {
            pendingDevice = existing
            monitor.requestPermission(existing)
        }
    }

    private fun connect(device: UsbDevice) {
        viewModelScope.launch {
            _state.update { it.copy(phase = FlashPhase.Connecting, error = null, needBootButton = false) }
            log("Detected ${UsbSerialTransportLabel(device)}")
            try {
                _state.update { it.copy(phase = FlashPhase.Detecting) }
                log("Connecting to bootloader…")
                val detected = repo.connect(monitor.manager(), device, _state.value.config)
                log("Chip: ${detected.chipId.label}")
                log("Flash size: ${detected.flashSizeLabel}")
                detected.macAddress?.let { log("MAC: $it") }
                _state.update { it.copy(phase = FlashPhase.Ready, device = detected) }
            } catch (t: Throwable) {
                log(t.message ?: "connect failed")
                val err = AppError.fromThrowable(t).let {
                    if (it.code == AppErrorCode.UNKNOWN) AppError.of(AppErrorCode.BOOTLOADER, t.message) else it
                }
                _state.update {
                    it.copy(
                        phase = FlashPhase.Error,
                        error = err,
                        needBootButton = err.code == AppErrorCode.BOOTLOADER,
                        device = null,
                    )
                }
                repo.disconnect()
            }
        }
    }

    fun addFirmware(uri: Uri, name: String, bytes: ByteArray) {
        val inspection = FirmwareInspector.inspect(bytes, name)
        val chip = _state.value.config.chipOverride ?: _state.value.device?.chipId ?: inspection.chipId
        val address = _state.value.config.flashAddress ?: inspection.suggestedAddress
        val image = FirmwareImage(name, bytes, address)
        _state.update {
            it.copy(images = it.images + image, inspections = it.inspections + inspection)
        }
        log("Firmware: $name (${bytes.size} bytes)")
        inspection.warnings.forEach { log(it) }
        chip // keep for future address override
        uri.toString()
    }

    fun consumeShareIntent(intent: Intent, readBytes: (Uri) -> Pair<String, ByteArray>?) {
        val uri = intent.data
            ?: if (intent.action == Intent.ACTION_SEND) {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } else null
        uri ?: return
        val file = readBytes(uri) ?: return
        addFirmware(uri, file.first, file.second)
    }

    fun removeFirmware() {
        _state.update { it.copy(images = emptyList(), inspections = emptyList()) }
    }

    fun requestFlash() {
        val s = _state.value
        if (s.device == null) {
            setError(AppError.of(AppErrorCode.NO_DEVICE)); return
        }
        if (s.images.isEmpty()) {
            setError(AppError.of(AppErrorCode.UNKNOWN, "Select a firmware image first.")); return
        }
        val compat = s.compatibility
        if (compat?.verdict == dev.espflasher.app.domain.CompatibilityVerdict.MISMATCH) {
            _state.update { it.copy(mismatchOpen = true) }
            return
        }
        flash(allowMismatch = false)
    }

    fun confirmMismatch() {
        _state.update { it.copy(mismatchOpen = false) }
        log("User overrode firmware mismatch — flashing anyway")
        flash(true)
    }

    fun cancelMismatch() {
        _state.update { it.copy(mismatchOpen = false) }
    }

    private fun flash(allowMismatch: Boolean) {
        if (!allowMismatch) {
            val compat = _state.value.compatibility
            if (compat?.verdict == dev.espflasher.app.domain.CompatibilityVerdict.MISMATCH) return
        }
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            try {
                val result = repo.flash(
                    images = _state.value.images,
                    config = _state.value.config,
                    onProgress = { p -> _state.update { it.copy(progress = p) } },
                    onLog = { log(it) },
                    onPhase = { name ->
                        val phase = when (name) {
                            "enteringBootloader" -> FlashPhase.EnteringBootloader
                            "erasing" -> FlashPhase.Erasing
                            "writing" -> FlashPhase.Writing
                            "verifying" -> FlashPhase.Verifying
                            "resetting" -> FlashPhase.Resetting
                            else -> FlashPhase.Writing
                        }
                        _state.update { it.copy(phase = phase) }
                    },
                )
                _state.update { it.copy(phase = FlashPhase.Success, result = result, progress = null) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(phase = FlashPhase.Error, error = AppError.fromThrowable(t), progress = null)
                }
                log(t.message ?: "flash failed")
            }
        }
    }

    fun cancelFlash() {
        flashJob?.cancel()
        viewModelScope.launch { repo.disconnect() }
        _state.update {
            it.copy(phase = FlashPhase.Error, error = AppError.of(AppErrorCode.CANCELLED), progress = null, device = null)
        }
    }

    fun resetToReady() {
        _state.update {
            it.copy(
                phase = if (it.device != null) FlashPhase.Ready else FlashPhase.Disconnected,
                error = null,
                result = null,
                progress = null,
                needBootButton = false,
            )
        }
    }

    fun updateConfig(transform: (FlashConfig) -> FlashConfig) {
        _state.update { it.copy(config = transform(it.config)) }
    }

    fun setTheme(theme: String) {
        _state.update { it.copy(theme = theme) }
    }

    fun setKeepAwake(value: Boolean) {
        _state.update { it.copy(keepAwake = value) }
    }

    private fun setError(error: AppError) {
        _state.update { it.copy(error = error, phase = FlashPhase.Error) }
    }

    private fun log(line: String) {
        _state.update { it.copy(logs = (it.logs + line).takeLast(400)) }
    }

    override fun onCleared() {
        viewModelScope.launch { repo.disconnect() }
        super.onCleared()
    }
}

private fun UsbSerialTransportLabel(device: UsbDevice) =
    dev.espflasher.app.usb.UsbSerialTransport.labelFor(device)

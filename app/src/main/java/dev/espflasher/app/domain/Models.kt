package dev.espflasher.app.domain

enum class ChipFamily { ESP8266, ESP32 }

enum class ChipId(val label: String, val family: ChipFamily, val imageChipId: Int?) {
    ESP8266("ESP8266", ChipFamily.ESP8266, null),
    ESP32("ESP32", ChipFamily.ESP32, 0x0000),
    ESP32_S2("ESP32-S2", ChipFamily.ESP32, 0x0002),
    ESP32_S3("ESP32-S3", ChipFamily.ESP32, 0x0009),
    ESP32_C3("ESP32-C3", ChipFamily.ESP32, 0x0005),
    ESP32_C6("ESP32-C6", ChipFamily.ESP32, 0x000D),
    ESP32_H2("ESP32-H2", ChipFamily.ESP32, 0x0010);

    val bootloaderOffset: Int
        get() = when (this) {
            ESP8266 -> 0x0
            ESP32, ESP32_S2 -> 0x1000
            else -> 0x0
        }

    val defaultFlashMode: String
        get() = if (this == ESP8266) "dout" else "dio"
}

enum class FlashPhase {
    Disconnected, Connecting, Detecting, Ready,
    EnteringBootloader, Erasing, Writing, Verifying, Resetting,
    Success, Error
}

data class DetectedDevice(
    val chipId: ChipId,
    val description: String,
    val flashSizeLabel: String,
    val flashSizeBytes: Long,
    val macAddress: String?,
    val usbLabel: String,
    val bootloaderReady: Boolean,
)

data class FirmwareImage(
    val name: String,
    val bytes: ByteArray,
    val address: Int,
) {
    val size: Int get() = bytes.size
}

data class FirmwareInspection(
    val valid: Boolean,
    val magicOk: Boolean,
    val family: ChipFamily?,
    val chipId: ChipId?,
    val suggestedAddress: Int,
    val isMerged: Boolean,
    val warnings: List<String>,
)

enum class CompatibilityVerdict { OK, MISMATCH, UNKNOWN }

data class Compatibility(
    val verdict: CompatibilityVerdict,
    val title: String,
    val message: String,
)

enum class AppErrorCode {
    NO_DEVICE, USB_PERMISSION, BOOTLOADER, DISCONNECT,
    WRONG_FIRMWARE, VERIFICATION, CANCELLED, TIMEOUT, UNKNOWN
}

data class AppError(
    val code: AppErrorCode,
    val title: String,
    val message: String,
    val action: String,
    val detail: String? = null,
) {
    companion object {
        fun of(code: AppErrorCode, detail: String? = null): AppError = when (code) {
            AppErrorCode.NO_DEVICE -> AppError(code, "No ESP device detected", "Connect an ESP8266 or ESP32 using a USB OTG adapter.", "Connect device", detail)
            AppErrorCode.USB_PERMISSION -> AppError(code, "USB permission required", "Android needs permission to communicate with this device.", "Grant permission", detail)
            AppErrorCode.BOOTLOADER -> AppError(code, "Couldn't enter bootloader", "Hold the BOOT button on your board, then reconnect USB.", "Try again", detail)
            AppErrorCode.DISCONNECT -> AppError(code, "Device disconnected", "The USB connection was lost during flashing.", "Reconnect", detail)
            AppErrorCode.WRONG_FIRMWARE -> AppError(code, "Firmware mismatch", "This firmware appears to target a different ESP chip.", "Choose another file", detail)
            AppErrorCode.VERIFICATION -> AppError(code, "Verification failed", "The firmware was written, but the verification step failed.", "Flash again", detail)
            AppErrorCode.CANCELLED -> AppError(code, "Flashing cancelled", "The flash operation was stopped before it finished.", "Done", detail)
            AppErrorCode.TIMEOUT -> AppError(code, "The board stopped responding", "The bootloader timed out. Reconnect the board and try again.", "Try again", detail)
            AppErrorCode.UNKNOWN -> AppError(code, "Something went wrong", "The flasher hit an unexpected error talking to the board.", "Try again", detail)
        }

        fun fromThrowable(t: Throwable): AppError {
            val m = t.message.orEmpty().lowercase()
            return when {
                "timeout" in m || "timed out" in m -> of(AppErrorCode.TIMEOUT, t.message)
                t is SecurityException -> of(AppErrorCode.USB_PERMISSION, t.message)
                "permission" in m -> of(AppErrorCode.USB_PERMISSION, t.message)
                "bootloader" in m || "sync" in m || "no serial" in m -> of(AppErrorCode.BOOTLOADER, t.message)
                "disconnect" in m || "device lost" in m -> of(AppErrorCode.DISCONNECT, t.message)
                "md5" in m || "verif" in m -> of(AppErrorCode.VERIFICATION, t.message)
                t is java.util.concurrent.CancellationException -> of(AppErrorCode.CANCELLED)
                else -> of(AppErrorCode.UNKNOWN, t.message)
            }
        }
    }
}

data class FlashProgress(
    val percent: Float,
    val written: Int,
    val total: Int,
    val bytesPerSec: Float,
)

data class FlashResult(
    val chipLabel: String,
    val firmwareName: String,
    val size: Int,
    val durationMs: Long,
    val verified: Boolean,
)

data class FlashConfig(
    val eraseBeforeFlash: Boolean = false,
    val verifyAfterFlash: Boolean = true,
    val baudRate: Int = 115200,
    val autoBootloader: Boolean = true,
    val flashMode: String = "keep",
    val flashFreq: String = "keep",
    val flashSize: String = "detect",
    val chipOverride: ChipId? = null,
    val flashAddress: Int? = null,
)

interface SerialTransport {
    suspend fun open(baud: Int)
    suspend fun close()
    suspend fun write(data: ByteArray)
    suspend fun read(max: Int, timeoutMs: Int): ByteArray
    suspend fun setDtr(value: Boolean)
    suspend fun setRts(value: Boolean)
    val usbLabel: String
}

interface EspFlasher {
    suspend fun connect()
    suspend fun detectChip(): DetectedDevice
    suspend fun getFlashInfo(): Long
    suspend fun eraseFlash()
    suspend fun writeFirmware(images: List<FirmwareImage>, onProgress: (FlashProgress) -> Unit)
    suspend fun verifyFirmware(images: List<FirmwareImage>)
    suspend fun reset()
    suspend fun disconnect()
}

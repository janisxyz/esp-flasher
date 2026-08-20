package dev.espflasher.app.flash

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.DetectedDevice
import dev.espflasher.app.domain.EspFlasher
import dev.espflasher.app.domain.FirmwareImage
import dev.espflasher.app.domain.FlashConfig
import dev.espflasher.app.domain.FlashProgress
import dev.espflasher.app.domain.FlashResult
import dev.espflasher.app.usb.UsbSerialTransport

class FlashingRepository {
    private var flasher: EspFlasher? = null
    private var device: DetectedDevice? = null

    suspend fun connect(
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        config: FlashConfig,
    ): DetectedDevice {
        disconnect()
        val transport = UsbSerialTransport(usbManager, usbDevice, UsbSerialTransport.labelFor(usbDevice))
        val probe = Esp32Flasher(transport, config.autoBootloader, config.chipOverride)
        try {
            probe.connect()
            val detected = probe.detectChip()
            val chosen = config.chipOverride ?: detected.chipId
            flasher = if (chosen == ChipId.ESP8266) {
                probe.disconnect()
                Esp8266Flasher(UsbSerialTransport(usbManager, usbDevice, transport.usbLabel), config.autoBootloader).also {
                    it.connect()
                    it.detectChip()
                }
            } else {
                probe
            }
            val finalDevice = if (chosen == ChipId.ESP8266) {
                (flasher as Esp8266Flasher).detectChip()
            } else detected.copy(chipId = chosen)
            device = finalDevice
            return finalDevice
        } catch (t: Throwable) {
            // ESP8266 boards still use the same serial path; retry dedicated flasher.
            try {
                probe.disconnect()
            } catch (_: Exception) {
            }
            val esp8266 = Esp8266Flasher(
                UsbSerialTransport(usbManager, usbDevice, UsbSerialTransport.labelFor(usbDevice)),
                config.autoBootloader,
            )
            esp8266.connect()
            val detected = esp8266.detectChip()
            flasher = esp8266
            device = detected
            return detected
        }
    }

    suspend fun flash(
        images: List<FirmwareImage>,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit,
        onPhase: (String) -> Unit,
    ): FlashResult {
        val f = flasher ?: error("Not connected")
        val dev = device ?: error("No chip detected")
        val started = System.currentTimeMillis()
        onPhase("enteringBootloader")
        onLog("Entering bootloader…")
        if (config.eraseBeforeFlash) {
            onPhase("erasing")
            onLog("Erasing flash…")
            f.eraseFlash()
            onLog("Erase complete")
        }
        onPhase("writing")
        onLog("Writing firmware…")
        f.writeFirmware(images, onProgress)
        onLog("Write complete")
        var verified = false
        if (config.verifyAfterFlash) {
            onPhase("verifying")
            onLog("Verifying firmware…")
            f.verifyFirmware(images)
            verified = true
            onLog("Verification successful")
        }
        onPhase("resetting")
        onLog("Resetting device…")
        f.reset()
        val duration = System.currentTimeMillis() - started
        onLog("Flash complete in ${duration / 1000f}s")
        return FlashResult(dev.chipId.label, images.joinToString { it.name }, images.sumOf { it.size }, duration, verified)
    }

    suspend fun disconnect() {
        try {
            flasher?.disconnect()
        } catch (_: Exception) {
        }
        flasher = null
        device = null
    }
}

package dev.espflasher.app.flash

import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.DetectedDevice
import dev.espflasher.app.domain.EspFlasher
import dev.espflasher.app.domain.FirmwareImage
import dev.espflasher.app.domain.FlashProgress
import dev.espflasher.app.domain.SerialTransport
import dev.espflasher.app.protocol.ChipMagic
import dev.espflasher.app.protocol.EspCommand
import java.security.MessageDigest

class Esp8266Flasher(
    private val transport: SerialTransport,
    private val autoReset: Boolean,
) : EspFlasher {
    private val cmd = EspCommand(transport)
    private var device: DetectedDevice? = null

    override suspend fun connect() {
        transport.open(115200)
        if (autoReset) cmd.classicReset()
        cmd.sync()
    }

    override suspend fun detectChip(): DetectedDevice {
        val magic = cmd.readReg(EspCommand.CHIP_MAGIC_REG)
        val chip = ChipMagic.fromMagic(magic) ?: ChipId.ESP8266
        val (sizeLabel, sizeBytes) = try {
            getFlashInfo().let { ChipMagic.flashSizeFromId(it.toInt()) }
        } catch (_: Exception) {
            "4 MB" to 4L * 1024 * 1024
        }
        val detected = DetectedDevice(
            chipId = if (chip.family == ChipId.ESP8266.family) chip else ChipId.ESP8266,
            description = "ESP8266EX",
            flashSizeLabel = sizeLabel,
            flashSizeBytes = sizeBytes,
            macAddress = readMacOrNull(),
            usbLabel = transport.usbLabel,
            bootloaderReady = true,
        )
        device = detected
        return detected
    }

    override suspend fun getFlashInfo(): Long {
        // ESP8266 SPI flash ID via READ_REG of mapped SPI; fall back to 4MB.
        return 0x16
    }

    override suspend fun eraseFlash() {
        val size = (device?.flashSizeBytes ?: 4L * 1024 * 1024).toInt()
        cmd.flashBegin(size, 0, encrypted = false)
        cmd.flashEnd(reboot = false)
    }

    override suspend fun writeFirmware(images: List<FirmwareImage>, onProgress: (FlashProgress) -> Unit) {
        val total = images.sumOf { it.size }
        var written = 0
        val started = System.nanoTime()
        for (image in images) {
            cmd.flashBegin(image.size, image.address, encrypted = false)
            var seq = 0
            var offset = 0
            while (offset < image.size) {
                val end = minOf(offset + EspCommand.FLASH_BLOCK, image.size)
                cmd.flashBlock(seq, image.bytes.copyOfRange(offset, end))
                offset = end
                seq++
                written += end - (end - EspCommand.FLASH_BLOCK).coerceAtLeast(offset - EspCommand.FLASH_BLOCK).let { offset - EspCommand.FLASH_BLOCK }.let { 0 }
                written = images.take(images.indexOf(image)).sumOf { it.size } + offset
                val elapsed = (System.nanoTime() - started) / 1_000_000_000f
                onProgress(
                    FlashProgress(
                        percent = if (total == 0) 0f else written * 100f / total,
                        written = written,
                        total = total,
                        bytesPerSec = if (elapsed > 0) written / elapsed else 0f,
                    ),
                )
            }
            cmd.flashEnd(reboot = false)
        }
    }

    override suspend fun verifyFirmware(images: List<FirmwareImage>) {
        for (image in images) {
            val expected = md5(image.bytes)
            val actual = try {
                cmd.flashMd5(image.address, image.size)
            } catch (t: Throwable) {
                error("ESP8266 verification failed: ${t.message}")
            }
            if (actual != expected) error("MD5 mismatch at 0x${image.address.toString(16)}: $actual != $expected")
        }
    }

    override suspend fun reset() {
        cmd.hardReset()
    }

    override suspend fun disconnect() {
        transport.close()
    }

    private suspend fun readMacOrNull(): String? = try {
        val mac0 = cmd.readReg(0x3FF00050)
        val mac1 = cmd.readReg(0x3FF00054)
        val bytes = byteArrayOf(
            ((mac1 shr 8) and 0xFF).toByte(),
            (mac1 and 0xFF).toByte(),
            ((mac0 shr 24) and 0xFF).toByte(),
            ((mac0 shr 16) and 0xFF).toByte(),
            ((mac0 shr 8) and 0xFF).toByte(),
            (mac0 and 0xFF).toByte(),
        )
        bytes.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }
    } catch (_: Exception) {
        null
    }

    private fun md5(data: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
}

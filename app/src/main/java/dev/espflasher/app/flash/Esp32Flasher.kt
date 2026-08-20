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

class Esp32Flasher(
    private val transport: SerialTransport,
    private val autoReset: Boolean,
    private val forcedChip: ChipId? = null,
) : EspFlasher {
    private val cmd = EspCommand(transport)
    private var device: DetectedDevice? = null

    override suspend fun connect() {
        transport.open(115200)
        if (autoReset) cmd.classicReset()
        cmd.sync()
        cmd.spiAttach()
    }

    override suspend fun detectChip(): DetectedDevice {
        val magic = cmd.readReg(EspCommand.CHIP_MAGIC_REG)
        val chip = forcedChip ?: ChipMagic.fromMagic(magic) ?: ChipId.ESP32
        val flashId = try {
            getFlashInfo()
        } catch (_: Exception) {
            0x16
        }
        val (label, bytes) = ChipMagic.flashSizeFromId(flashId.toInt())
        try {
            cmd.spiSetParams(bytes)
        } catch (_: Exception) {
        }
        val detected = DetectedDevice(
            chipId = chip,
            description = chip.label,
            flashSizeLabel = label,
            flashSizeBytes = bytes,
            macAddress = readMacOrNull(chip),
            usbLabel = transport.usbLabel,
            bootloaderReady = true,
        )
        device = detected
        return detected
    }

    override suspend fun getFlashInfo(): Long {
        // SPI flash RDID command 0x9F via ROM SPI; without stub we approximate 4MB.
        return 0x16
    }

    override suspend fun eraseFlash() {
        try {
            cmd.eraseFlash()
        } catch (_: Exception) {
            val size = (device?.flashSizeBytes ?: 4L * 1024 * 1024).toInt()
            cmd.flashBegin(size, 0, encrypted = true)
            cmd.flashEnd(reboot = false)
        }
    }

    override suspend fun writeFirmware(images: List<FirmwareImage>, onProgress: (FlashProgress) -> Unit) {
        val total = images.sumOf { it.size }
        val started = System.nanoTime()
        var done = 0
        for (image in images) {
            cmd.flashBegin(image.size, image.address, encrypted = true)
            var seq = 0
            var offset = 0
            while (offset < image.size) {
                val end = minOf(offset + EspCommand.FLASH_BLOCK, image.size)
                cmd.flashBlock(seq, image.bytes.copyOfRange(offset, end))
                offset = end
                seq++
                done = images.take(images.indexOf(image)).sumOf { it.size } + offset
                val elapsed = (System.nanoTime() - started) / 1_000_000_000f
                onProgress(
                    FlashProgress(
                        percent = if (total == 0) 0f else done * 100f / total,
                        written = done,
                        total = total,
                        bytesPerSec = if (elapsed > 0) done / elapsed else 0f,
                    ),
                )
            }
            cmd.flashEnd(reboot = false)
        }
    }

    override suspend fun verifyFirmware(images: List<FirmwareImage>) {
        for (image in images) {
            val expected = md5(image.bytes)
            val actual = cmd.flashMd5(image.address, image.size)
            if (actual != expected) {
                error("MD5 mismatch at 0x${image.address.toString(16)}: $actual != $expected")
            }
        }
    }

    override suspend fun reset() {
        cmd.hardReset()
    }

    override suspend fun disconnect() {
        transport.close()
    }

    private suspend fun readMacOrNull(chip: ChipId): String? = try {
        // ESP32 MAC in eFuse block 0; variants differ. Best-effort.
        val mac0 = cmd.readReg(if (chip == ChipId.ESP32) 0x3FF5A004 else 0x6001A000)
        val mac1 = cmd.readReg(if (chip == ChipId.ESP32) 0x3FF5A008 else 0x6001A004)
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

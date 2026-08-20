package dev.espflasher.app

import dev.espflasher.app.domain.AppError
import dev.espflasher.app.domain.AppErrorCode
import dev.espflasher.app.domain.ChipFamily
import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.CompatibilityVerdict
import dev.espflasher.app.domain.FirmwareInspector
import dev.espflasher.app.domain.FlashConfig
import dev.espflasher.app.domain.FlashPhase
import dev.espflasher.app.domain.FlashStateMachine
import dev.espflasher.app.protocol.ChipMagic
import dev.espflasher.app.protocol.Slip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class FirmwareInspectorTest {
    private fun u32(n: Int) = byteArrayOf(
        (n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte(),
        ((n shr 16) and 0xFF).toByte(), ((n shr 24) and 0xFF).toByte(),
    )

    private fun esp8266(): ByteArray {
        val header = byteArrayOf(0xE9.toByte(), 1, 3, 0x40) + u32(0x40100000) + u32(0x40100000) + u32(16)
        return header + ByteArray(16) { 0xAA.toByte() }
    }

    private fun esp32s3(): ByteArray {
        val rest = ByteArray(16)
        rest[0] = 0xEE.toByte()
        rest[4] = 0x09
        rest[5] = 0x00
        return byteArrayOf(0xE9.toByte(), 2, 2, 0x20) + u32(0x40000400) + rest
    }

    private fun esp32Classic(): ByteArray {
        val rest = ByteArray(16)
        rest[0] = 0xEE.toByte()
        rest[4] = 0x00
        rest[5] = 0x00
        return byteArrayOf(0xE9.toByte(), 3, 2, 0x20) + u32(0x40080400) + rest
    }

    @Test fun esp8266Image() {
        val info = FirmwareInspector.inspect(esp8266(), "nodemcu.bin")
        assertTrue(info.valid)
        assertEquals(ChipId.ESP8266, info.chipId)
        assertEquals(ChipFamily.ESP8266, info.family)
        assertEquals(0, info.suggestedAddress)
    }

    @Test fun esp32s3Image() {
        val info = FirmwareInspector.inspect(esp32s3(), "s3.bin")
        assertTrue(info.valid)
        assertEquals(ChipId.ESP32_S3, info.chipId)
        assertEquals(ChipFamily.ESP32, info.family)
    }

    @Test fun esp32ClassicImage() {
        val info = FirmwareInspector.inspect(esp32Classic(), "app.bin")
        assertTrue(info.valid)
        assertEquals(ChipFamily.ESP32, info.family)
        assertEquals(ChipId.ESP32, info.chipId)
    }

    @Test fun invalidImage() {
        val info = FirmwareInspector.inspect(byteArrayOf(1, 2, 3, 4, 5), "x.bin")
        assertFalse(info.valid)
        assertFalse(info.magicOk)
    }

    @Test fun tinyFileIsInvalid() {
        val info = FirmwareInspector.inspect(byteArrayOf(0xE9.toByte()), "tiny.bin")
        assertFalse(info.valid)
    }

    @Test fun mergedImageSuggestsAddressZero() {
        val merged = ByteArray(0x20080)
        merged[0] = 0xE9.toByte()
        merged[0x10000] = 0xE9.toByte()
        val info = FirmwareInspector.inspect(merged, "merged.bin")
        assertTrue(info.isMerged)
        assertEquals(0, info.suggestedAddress)
    }

    @Test fun mismatchEsp32OnEsp8266() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp32s3()), ChipId.ESP8266)
        assertEquals(CompatibilityVerdict.MISMATCH, c.verdict)
    }

    @Test fun familyMismatchEsp8266OnEsp32() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp8266()), ChipId.ESP32)
        assertEquals(CompatibilityVerdict.MISMATCH, c.verdict)
    }

    @Test fun compatible() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp32s3()), ChipId.ESP32_S3)
        assertEquals(CompatibilityVerdict.OK, c.verdict)
    }

    @Test fun unknownWhenNoDevice() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp32s3()), null)
        assertEquals(CompatibilityVerdict.UNKNOWN, c.verdict)
    }
}

class ChipDetectionTest {
    @Test fun magics() {
        assertEquals(ChipId.ESP8266, ChipMagic.fromMagic(0xFFF0C101))
        assertEquals(ChipId.ESP32, ChipMagic.fromMagic(0x00F01D83))
        assertEquals(ChipId.ESP32_S2, ChipMagic.fromMagic(0x000007C6))
        assertEquals(ChipId.ESP32_S3, ChipMagic.fromMagic(0x00000009))
        assertEquals(ChipId.ESP32_C3, ChipMagic.fromMagic(0x6921506F))
        assertEquals(ChipId.ESP32_C3, ChipMagic.fromMagic(0x1B31506F))
        assertEquals(ChipId.ESP32_C6, ChipMagic.fromMagic(0x2CE0806F))
        assertEquals(ChipId.ESP32_C6, ChipMagic.fromMagic(0xCA140BF0))
        assertEquals(ChipId.ESP32_H2, ChipMagic.fromMagic(0xD7B73E80))
        assertEquals(ChipId.ESP32_H2, ChipMagic.fromMagic(0x9E00006F))
        assertNull(ChipMagic.fromMagic(0xDEADBEEF))
    }

    @Test fun flashSizeFromId() {
        assertEquals("256 KB" to 256L * 1024, ChipMagic.flashSizeFromId(0x12))
        assertEquals("4 MB" to 4L * 1024 * 1024, ChipMagic.flashSizeFromId(0x16))
        assertEquals("16 MB" to 16L * 1024 * 1024, ChipMagic.flashSizeFromId(0x18))
        assertEquals("detect" to 4L * 1024 * 1024, ChipMagic.flashSizeFromId(0x00))
    }

    @Test fun bootloaderOffsets() {
        assertEquals(0x0, ChipId.ESP8266.bootloaderOffset)
        assertEquals(0x1000, ChipId.ESP32.bootloaderOffset)
        assertEquals(0x1000, ChipId.ESP32_S2.bootloaderOffset)
        assertEquals(0x0, ChipId.ESP32_S3.bootloaderOffset)
        assertEquals(0x0, ChipId.ESP32_C3.bootloaderOffset)
    }
}

class StateMachineTest {
    @Test fun legal() {
        assertTrue(FlashStateMachine.canTransition(FlashPhase.Disconnected, FlashPhase.Connecting))
        assertTrue(FlashStateMachine.canTransition(FlashPhase.Writing, FlashPhase.Verifying))
        assertEquals(FlashPhase.Disconnected, FlashStateMachine.apply(FlashPhase.Writing, FlashPhase.Disconnected))
        assertTrue(FlashStateMachine.isFlashing(FlashPhase.Writing))
        assertFalse(FlashStateMachine.isFlashing(FlashPhase.Ready))
    }

    @Test fun usbDisconnectAlwaysAllowed() {
        assertEquals(FlashPhase.Disconnected, FlashStateMachine.apply(FlashPhase.Erasing, FlashPhase.Disconnected))
        assertEquals(FlashPhase.Disconnected, FlashStateMachine.apply(FlashPhase.Verifying, FlashPhase.Disconnected))
        assertFalse(FlashStateMachine.canTransition(FlashPhase.Success, FlashPhase.Error))
        assertEquals(FlashPhase.Error, FlashStateMachine.apply(FlashPhase.Success, FlashPhase.Error))
    }

    @Test fun illegalCanTransition() {
        assertFalse(FlashStateMachine.canTransition(FlashPhase.Success, FlashPhase.Connecting))
        assertFalse(FlashStateMachine.canTransition(FlashPhase.Disconnected, FlashPhase.Writing))
    }

    @Test fun labels() {
        assertEquals("Waiting for device", FlashStateMachine.label(FlashPhase.Disconnected))
        assertEquals("Writing firmware", FlashStateMachine.label(FlashPhase.Writing))
        assertEquals("Complete", FlashStateMachine.label(FlashPhase.Success))
    }

    @Test(expected = IllegalStateException::class)
    fun illegal() {
        FlashStateMachine.apply(FlashPhase.Disconnected, FlashPhase.Writing)
    }
}

class SlipTest {
    @Test fun roundTrip() {
        val payload = byteArrayOf(0x00, 0xC0.toByte(), 0xDB.toByte(), 0x01)
        val encoded = Slip.encode(payload)
        val decoded = Slip.decode(encoded)
        assertEquals(1, decoded.size)
        assertTrue(payload.contentEquals(decoded[0]))
        assertEquals(Slip.END, encoded.first())
        assertEquals(Slip.END, encoded.last())
    }

    @Test fun emptyStreamHasNoFrames() {
        assertTrue(Slip.decode(ByteArray(0)).isEmpty())
        assertTrue(Slip.decode(byteArrayOf(Slip.END, Slip.END)).isEmpty())
    }
}

class ErrorMappingTest {
    @Test fun bootloader() {
        val err = AppError.fromThrowable(RuntimeException("SYNC timed out"))
        assertEquals(AppErrorCode.TIMEOUT, err.code)
    }

    @Test fun disconnect() {
        val err = AppError.fromThrowable(RuntimeException("device lost"))
        assertEquals(AppErrorCode.DISCONNECT, err.code)
    }

    @Test fun usbPermission() {
        assertEquals(AppErrorCode.USB_PERMISSION, AppError.fromThrowable(SecurityException("denied")).code)
        assertEquals(AppErrorCode.USB_PERMISSION, AppError.fromThrowable(RuntimeException("USB permission missing")).code)
    }

    @Test fun verification() {
        assertEquals(AppErrorCode.VERIFICATION, AppError.fromThrowable(RuntimeException("MD5 of file does not match")).code)
        assertEquals(AppErrorCode.VERIFICATION, AppError.fromThrowable(RuntimeException("verify failed")).code)
    }

    @Test fun cancelled() {
        assertEquals(AppErrorCode.CANCELLED, AppError.fromThrowable(CancellationException()).code)
    }

    @Test fun bootloaderMessage() {
        assertEquals(AppErrorCode.BOOTLOADER, AppError.fromThrowable(RuntimeException("no serial driver")).code)
        assertEquals(AppErrorCode.BOOTLOADER, AppError.fromThrowable(RuntimeException("couldn't enter bootloader")).code)
    }
}

class FlashConfigTest {
    @Test fun defaults() {
        val cfg = FlashConfig()
        assertFalse(cfg.eraseBeforeFlash)
        assertTrue(cfg.verifyAfterFlash)
        assertEquals(115200, cfg.baudRate)
        assertTrue(cfg.autoBootloader)
        assertEquals("keep", cfg.flashMode)
        assertEquals("detect", cfg.flashSize)
        assertNull(cfg.chipOverride)
        assertNull(cfg.flashAddress)
    }
}

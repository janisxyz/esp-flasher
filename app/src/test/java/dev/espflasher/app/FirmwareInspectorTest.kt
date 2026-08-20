package dev.espflasher.app

import dev.espflasher.app.domain.ChipId
import dev.espflasher.app.domain.CompatibilityVerdict
import dev.espflasher.app.domain.FirmwareInspector
import dev.espflasher.app.domain.FlashPhase
import dev.espflasher.app.domain.FlashStateMachine
import dev.espflasher.app.protocol.ChipMagic
import dev.espflasher.app.protocol.Slip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun esp8266Image() {
        val info = FirmwareInspector.inspect(esp8266(), "nodemcu.bin")
        assertTrue(info.valid)
        assertEquals(ChipId.ESP8266, info.chipId)
        assertEquals(0, info.suggestedAddress)
    }

    @Test fun esp32s3Image() {
        val info = FirmwareInspector.inspect(esp32s3(), "s3.bin")
        assertTrue(info.valid)
        assertEquals(ChipId.ESP32_S3, info.chipId)
    }

    @Test fun invalidImage() {
        val info = FirmwareInspector.inspect(byteArrayOf(1, 2, 3, 4, 5), "x.bin")
        assertFalse(info.valid)
    }

    @Test fun mismatchEsp32OnEsp8266() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp32s3()), ChipId.ESP8266)
        assertEquals(CompatibilityVerdict.MISMATCH, c.verdict)
    }

    @Test fun compatible() {
        val c = FirmwareInspector.compatibility(FirmwareInspector.inspect(esp32s3()), ChipId.ESP32_S3)
        assertEquals(CompatibilityVerdict.OK, c.verdict)
    }
}

class ChipDetectionTest {
    @Test fun magics() {
        assertEquals(ChipId.ESP8266, ChipMagic.fromMagic(0xFFF0C101))
        assertEquals(ChipId.ESP32, ChipMagic.fromMagic(0x00F01D83))
        assertEquals(ChipId.ESP32_S3, ChipMagic.fromMagic(0x00000009))
        assertEquals(ChipId.ESP32_C3, ChipMagic.fromMagic(0x6921506F))
        assertEquals(ChipId.ESP32_C6, ChipMagic.fromMagic(0x2CE0806F))
        assertEquals(ChipId.ESP32_H2, ChipMagic.fromMagic(0xD7B73E80))
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
    }
}

class ErrorMappingTest {
    @Test fun bootloader() {
        val err = dev.espflasher.app.domain.AppError.fromThrowable(RuntimeException("SYNC timed out"))
        assertEquals(dev.espflasher.app.domain.AppErrorCode.TIMEOUT, err.code)
    }

    @Test fun disconnect() {
        val err = dev.espflasher.app.domain.AppError.fromThrowable(RuntimeException("device lost"))
        assertEquals(dev.espflasher.app.domain.AppErrorCode.DISCONNECT, err.code)
    }
}

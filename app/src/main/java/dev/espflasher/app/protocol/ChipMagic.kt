package dev.espflasher.app.protocol

import dev.espflasher.app.domain.ChipId

object ChipMagic {
    private val map = mapOf(
        0xFFF0C101.toInt() to ChipId.ESP8266,
        0x00F01D83 to ChipId.ESP32,
        0x000007C6 to ChipId.ESP32_S2,
        0x00000009 to ChipId.ESP32_S3,
        0x6921506F to ChipId.ESP32_C3,
        0x1B31506F to ChipId.ESP32_C3,
        0x2CE0806F to ChipId.ESP32_C6,
        0xCA140BF0.toInt() to ChipId.ESP32_C6,
        0xD7B73E80.toInt() to ChipId.ESP32_H2,
        0x9E00006F.toInt() to ChipId.ESP32_H2,
    )

    fun fromMagic(magic: Long): ChipId? = map[magic.toInt()]

    fun flashSizeFromId(id: Int): Pair<String, Long> {
        // SPI flash RDID density nibble mapping used by esptool.
        val density = id and 0xFF
        return when (density) {
            0x12 -> "256 KB" to 256L * 1024
            0x13 -> "512 KB" to 512L * 1024
            0x14 -> "1 MB" to 1L * 1024 * 1024
            0x15 -> "2 MB" to 2L * 1024 * 1024
            0x16 -> "4 MB" to 4L * 1024 * 1024
            0x17 -> "8 MB" to 8L * 1024 * 1024
            0x18 -> "16 MB" to 16L * 1024 * 1024
            0x19 -> "32 MB" to 32L * 1024 * 1024
            else -> "detect" to 4L * 1024 * 1024
        }
    }
}

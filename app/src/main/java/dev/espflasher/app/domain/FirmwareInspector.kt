package dev.espflasher.app.domain

object FirmwareInspector {
    const val MAGIC = 0xE9
    private val MAGIC_OFFSETS = intArrayOf(0x0, 0x1000, 0x8000, 0x10000, 0x20000)

    fun inspect(data: ByteArray, name: String = "firmware.bin"): FirmwareInspection {
        if (data.size < 8) {
            return FirmwareInspection(false, false, null, null, 0, false, listOf("File is too small to be an ESP firmware image."))
        }
        val magics = MAGIC_OFFSETS.filter { it < data.size && data[it].toInt() and 0xFF == MAGIC }
        val magicOk = magics.isNotEmpty() || (data[0].toInt() and 0xFF == MAGIC)
        val warnings = mutableListOf<String>()
        if (!magicOk) warnings += "$name does not start with ESP image magic (0xE9)."

        val headerAt = magics.firstOrNull() ?: 0
        val (family, chip) = if (headerAt < data.size && data[headerAt].toInt() and 0xFF == MAGIC) {
            parseHeader(data, headerAt)
        } else null to null

        val hasApp = magics.contains(0x10000)
        val hasBoot0 = magics.contains(0x0)
        val hasBoot1000 = magics.contains(0x1000)
        val merged = data.size >= 0x20000 && ((hasBoot0 && hasApp) || (hasBoot1000 && hasApp))

        val address = when {
            merged -> 0
            family == ChipFamily.ESP8266 -> 0
            chip != null && data.size < 0x8000 -> 0x10000
            chip != null -> chip.bootloaderOffset
            else -> 0
        }

        return FirmwareInspection(magicOk || merged, magicOk, family, chip, address, merged, warnings)
    }

    fun compatibility(inspection: FirmwareInspection, device: ChipId?): Compatibility {
        if (device == null) return Compatibility(CompatibilityVerdict.UNKNOWN, "No device connected", "Connect a board to check this firmware.")
        if (!inspection.valid && inspection.family == null) {
            return Compatibility(CompatibilityVerdict.UNKNOWN, "Could not identify firmware", "This file does not look like a standard ESP image.")
        }
        if (inspection.family != null && inspection.family != device.family) {
            val fw = inspection.chipId?.label ?: inspection.family.name
            return Compatibility(CompatibilityVerdict.MISMATCH, "Firmware mismatch", "Selected firmware appears to target $fw, but the connected device is ${device.label}.")
        }
        if (inspection.chipId != null && inspection.chipId != device && inspection.family == ChipFamily.ESP32) {
            return Compatibility(CompatibilityVerdict.MISMATCH, "Firmware mismatch", "Selected firmware appears to target ${inspection.chipId.label}, but the connected device is ${device.label}.")
        }
        return Compatibility(CompatibilityVerdict.OK, "Compatible", "Firmware looks like a valid ${device.label} image.")
    }

    private fun parseHeader(data: ByteArray, offset: Int): Pair<ChipFamily?, ChipId?> {
        if (offset + 24 <= data.size) {
            val wp = data[offset + 8].toInt() and 0xFF
            val hashAppended = data[offset + 23].toInt() and 0xFF
            val chipId = (data[offset + 12].toInt() and 0xFF) or ((data[offset + 13].toInt() and 0xFF) shl 8)
            if (hashAppended <= 1) {
                ChipId.entries.firstOrNull { it.imageChipId == chipId && it != ChipId.ESP32 }?.let {
                    return ChipFamily.ESP32 to it
                }
                if (chipId == 0 && wp in listOf(0xEE, 0xFF)) {
                    val segs = data[offset + 1].toInt() and 0xFF
                    if (segs in 1..15) return ChipFamily.ESP32 to ChipId.ESP32
                }
            }
        }
        if (offset + 16 <= data.size) {
            val size = readU32(data, offset + 12)
            if (size in 1 until data.size) return ChipFamily.ESP8266 to ChipId.ESP8266
        }
        return null to null
    }

    private fun readU32(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) return 0
        return (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
}

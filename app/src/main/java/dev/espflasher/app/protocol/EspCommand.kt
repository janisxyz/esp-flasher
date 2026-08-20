package dev.espflasher.app.protocol

import dev.espflasher.app.domain.SerialTransport
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EspCommand(private val transport: SerialTransport) {
    companion object {
        const val DIR_REQ: Byte = 0x00
        const val DIR_RESP: Byte = 0x01
        const val SYNC: Int = 0x08
        const val READ_REG: Int = 0x0A
        const val WRITE_REG: Int = 0x09
        const val FLASH_BEGIN: Int = 0x02
        const val FLASH_DATA: Int = 0x03
        const val FLASH_END: Int = 0x04
        const val SPI_SET_PARAMS: Int = 0x0B
        const val SPI_ATTACH: Int = 0x0D
        const val CHANGE_BAUD: Int = 0x0F
        const val FLASH_MD5: Int = 0x13
        const val ERASE_FLASH: Int = 0xD0
        const val CHECKSUM_MAGIC: Int = 0xEF
        const val CHIP_MAGIC_REG: Long = 0x40001000L
        const val FLASH_BLOCK: Int = 0x400
    }

    data class Response(val value: Long, val data: ByteArray)

    suspend fun sync() {
        val payload = ByteArray(36)
        payload[0] = 0x07; payload[1] = 0x07; payload[2] = 0x12; payload[3] = 0x20
        for (i in 4 until 36) payload[i] = 0x55
        var last: Throwable? = null
        repeat(8) {
            try {
                command(SYNC, payload, timeoutMs = 300, checksum = 0)
                return
            } catch (t: Throwable) {
                last = t
                delay(50)
            }
        }
        throw last ?: IllegalStateException("SYNC failed")
    }

    suspend fun readReg(address: Long): Long {
        val data = u32(address)
        return command(READ_REG, data, timeoutMs = 1000).value
    }

    suspend fun spiAttach() {
        command(SPI_ATTACH, ByteArray(8), timeoutMs = 1000)
    }

    suspend fun spiSetParams(flashSize: Long) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0)
        buf.putInt(flashSize.toInt())
        buf.putInt(64 * 1024)
        buf.putInt(4 * 1024)
        buf.putInt(256)
        buf.putInt(0xFFFF)
        command(SPI_SET_PARAMS, buf.array(), timeoutMs = 1000)
    }

    suspend fun flashBegin(size: Int, offset: Int, encrypted: Boolean) {
        val blocks = (size + FLASH_BLOCK - 1) / FLASH_BLOCK
        val buf = ByteBuffer.allocate(if (encrypted) 20 else 16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(size)
        buf.putInt(blocks)
        buf.putInt(FLASH_BLOCK)
        buf.putInt(offset)
        if (encrypted) buf.putInt(0)
        command(FLASH_BEGIN, buf.array(), timeoutMs = 30_000)
    }

    suspend fun flashBlock(sequence: Int, chunk: ByteArray) {
        val padded = ByteArray(FLASH_BLOCK)
        chunk.copyInto(padded)
        val header = ByteBuffer.allocate(16 + FLASH_BLOCK).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(padded.size)
        header.putInt(sequence)
        header.putInt(0)
        header.putInt(0)
        header.put(padded)
        val cs = checksum(padded)
        command(FLASH_DATA, header.array(), timeoutMs = 5000, checksum = cs)
    }

    suspend fun flashEnd(reboot: Boolean) {
        val stay = if (reboot) 0 else 1
        command(FLASH_END, u32(stay.toLong()), timeoutMs = 2000)
    }

    suspend fun eraseFlash() {
        command(ERASE_FLASH, ByteArray(0), timeoutMs = 60_000)
    }

    suspend fun flashMd5(address: Int, size: Int): String {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(address)
        buf.putInt(size)
        buf.putInt(0)
        buf.putInt(0)
        val resp = command(FLASH_MD5, buf.array(), timeoutMs = 30_000)
        val hex = resp.data.take(32).map { it.toInt().toChar() }.joinToString("")
        if (hex.length >= 32) return hex.take(32).lowercase()
        return resp.data.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(32)
    }

    suspend fun classicReset() {
        transport.setDtr(false)
        transport.setRts(true)
        delay(100)
        transport.setDtr(true)
        transport.setRts(false)
        delay(50)
        transport.setDtr(false)
        delay(50)
    }

    suspend fun hardReset() {
        transport.setDtr(false)
        transport.setRts(true)
        delay(100)
        transport.setRts(false)
        delay(50)
    }

    private suspend fun command(
        op: Int,
        data: ByteArray,
        timeoutMs: Int,
        checksum: Int = 0,
    ): Response {
        val pkt = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        pkt.put(DIR_REQ)
        pkt.put(op.toByte())
        pkt.putShort(data.size.toShort())
        pkt.putInt(checksum)
        pkt.put(data)
        transport.write(Slip.encode(pkt.array()))

        val deadline = System.currentTimeMillis() + timeoutMs
        val acc = ArrayList<Byte>()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(1024, 80)
            if (chunk.isEmpty()) continue
            acc.addAll(chunk.toList())
            val frames = Slip.decode(acc.toByteArray())
            for (frame in frames) {
                if (frame.size < 8) continue
                if (frame[0] != DIR_RESP) continue
                if ((frame[1].toInt() and 0xFF) != op) continue
                val value = ByteBuffer.wrap(frame, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val payload = if (frame.size > 8) frame.copyOfRange(8, frame.size) else ByteArray(0)
                if (payload.size >= 2) {
                    val status = payload[payload.size - 2].toInt() and 0xFF
                    if (status != 0) {
                        val err = payload[payload.size - 1].toInt() and 0xFF
                        error("ESP command 0x${op.toString(16)} failed status=$status err=$err")
                    }
                }
                return Response(value, payload)
            }
        }
        error("Timed out waiting for command 0x${op.toString(16)}")
    }

    private fun checksum(data: ByteArray): Int {
        var c = CHECKSUM_MAGIC
        for (b in data) c = c xor (b.toInt() and 0xFF)
        return c
    }

    private fun u32(n: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n.toInt()).array()
}

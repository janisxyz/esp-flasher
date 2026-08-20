package dev.espflasher.app.protocol

object Slip {
    const val END: Byte = 0xC0.toByte()
    const val ESC: Byte = 0xDB.toByte()
    const val ESC_END: Byte = 0xDC.toByte()
    const val ESC_ESC: Byte = 0xDD.toByte()

    fun encode(payload: ByteArray): ByteArray {
        val out = ArrayList<Byte>(payload.size + 4)
        out.add(END)
        for (b in payload) {
            when (b) {
                END -> {
                    out.add(ESC); out.add(ESC_END)
                }
                ESC -> {
                    out.add(ESC); out.add(ESC_ESC)
                }
                else -> out.add(b)
            }
        }
        out.add(END)
        return out.toByteArray()
    }

    fun decode(stream: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val cur = ArrayList<Byte>()
        var inFrame = false
        var esc = false
        for (b in stream) {
            if (!inFrame) {
                if (b == END) inFrame = true
                continue
            }
            if (esc) {
                when (b) {
                    ESC_END -> cur.add(END)
                    ESC_ESC -> cur.add(ESC)
                    else -> cur.add(b)
                }
                esc = false
                continue
            }
            when (b) {
                ESC -> esc = true
                END -> {
                    if (cur.isNotEmpty()) frames.add(cur.toByteArray())
                    cur.clear()
                }
                else -> cur.add(b)
            }
        }
        return frames
    }
}

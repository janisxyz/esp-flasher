package dev.espflasher.app.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dev.espflasher.app.domain.SerialTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbSerialTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    override val usbLabel: String,
) : SerialTransport {

    private var port: UsbSerialPort? = null

    override suspend fun open(baud: Int) = withContext(Dispatchers.IO) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("No USB serial driver for ${device.deviceName}")
        val connection = usbManager.openDevice(device)
            ?: throw SecurityException("USB permission not granted")
        val p = driver.ports.first()
        p.open(connection)
        p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        p.dtr = false
        p.rts = false
        port = p
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val p = port ?: error("Serial port closed")
        var offset = 0
        while (offset < data.size) {
            val slice = if (offset == 0) data else data.copyOfRange(offset, data.size)
            val n = p.write(slice, 1000)
            if (n <= 0) error("USB serial write failed")
            offset += n
        }
    }

    override suspend fun read(max: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val p = port ?: return@withContext ByteArray(0)
        val buf = ByteArray(max)
        val n = try {
            p.read(buf, timeoutMs)
        } catch (_: Exception) {
            0
        }
        if (n <= 0) ByteArray(0) else buf.copyOf(n)
    }

    override suspend fun setDtr(value: Boolean) = withContext(Dispatchers.IO) {
        port?.dtr = value
    }

    override suspend fun setRts(value: Boolean) = withContext(Dispatchers.IO) {
        port?.rts = value
    }

    companion object {
        fun labelFor(device: UsbDevice): String = when (device.vendorId) {
            0x1A86 -> "CH340 / CH341"
            0x10C4 -> "Silicon Labs CP210x"
            0x0403 -> "FTDI"
            0x303A -> "Espressif native USB"
            0x067B -> "Prolific PL2303"
            else -> "USB Serial %04x:%04x".format(device.vendorId, device.productId)
        }
    }
}

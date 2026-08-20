package dev.espflasher.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface UsbEvent {
    data class Attached(val device: UsbDevice) : UsbEvent
    data class Permission(val device: UsbDevice, val granted: Boolean) : UsbEvent
    data class Detached(val device: UsbDevice) : UsbEvent
}

class UsbMonitor(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "${context.packageName}.USB_PERMISSION"

    val events: Flow<UsbEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                } ?: return
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> trySend(UsbEvent.Attached(device))
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> trySend(UsbEvent.Detached(device))
                    permissionAction -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        trySend(UsbEvent.Permission(device, granted))
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    fun listSerialDevices(): List<UsbDevice> {
        val probed = UsbSerialProber.getDefaultProber().findAllDrivers(manager).map { it.device }
        if (probed.isNotEmpty()) return probed
        return manager.deviceList.values.toList()
    }

    fun hasPermission(device: UsbDevice) = manager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, Intent(permissionAction), flags)
        manager.requestPermission(device, pi)
    }

    fun manager(): UsbManager = manager
}

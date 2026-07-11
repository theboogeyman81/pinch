package com.pinch.gary.glasses.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.model.GlassesEvent
import com.pinch.gary.glasses.model.RadarGestureType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleConnectionManager"

sealed interface GattConnectionEvent {
    data object Connected : GattConnectionEvent
    data object ServicesReady : GattConnectionEvent
    data object Disconnected : GattConnectionEvent
    data class GlassesEventReceived(val event: GlassesEvent) : GattConnectionEvent
}

/**
 * Owns the [BluetoothGatt] connection lifecycle: connect, discover services,
 * subscribe to notification characteristics, decode incoming bytes into
 * [GlassesEvent]. [com.pinch.gary.glasses.GlassesManagerImpl] drives the
 * higher-level state machine on top of this.
 */
@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Flow<GattConnectionEvent> = callbackFlow {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Logger.d(TAG, "GATT connected, discovering services")
                        trySend(GattConnectionEvent.Connected)
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Logger.d(TAG, "GATT disconnected (status=$status)")
                        trySend(GattConnectionEvent.Disconnected)
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Logger.w(TAG, "Service discovery failed with status $status")
                    return
                }
                subscribeToNotifications(g)
                trySend(GattConnectionEvent.ServicesReady)
            }

            // API 33+ delivers the value directly; API 26-32 only calls the
            // deprecated 2-arg overload below and the value must be read off
            // the characteristic. Both are overridden since minSdk is 26.
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                decodeEvent(characteristic.uuid, value)?.let {
                    trySend(GattConnectionEvent.GlassesEventReceived(it))
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) return
                val value = characteristic.value ?: return
                decodeEvent(characteristic.uuid, value)?.let {
                    trySend(GattConnectionEvent.GlassesEventReceived(it))
                }
            }
        }

        gatt = device.connectGatt(context, false, callback)
        Logger.d(TAG, "connectGatt() called for ${device.address}")

        awaitClose {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToNotifications(g: BluetoothGatt) {
        val service = g.getService(BleGattProfile.SERVICE_UUID) ?: run {
            Logger.w(TAG, "Glasses GATT service not found after discovery")
            return
        }

        listOf(
            BleGattProfile.TOUCH_EVENT_CHARACTERISTIC,
            BleGattProfile.BATTERY_CHARACTERISTIC,
            BleGattProfile.RADAR_GESTURE_CHARACTERISTIC
        ).forEach { uuid ->
            val characteristic = service.getCharacteristic(uuid) ?: return@forEach
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(
                BleGattProfile.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR
            )
            if (descriptor != null) {
                writeEnableNotification(g, descriptor)
            }
        }
    }

    /** Firmware wire format TBD (hardware/ not built yet) — single-byte codes are a placeholder. */
    private fun decodeEvent(characteristicUuid: java.util.UUID, value: ByteArray): GlassesEvent? {
        if (value.isEmpty()) return null
        return when (characteristicUuid) {
            BleGattProfile.TOUCH_EVENT_CHARACTERISTIC ->
                GlassesEvent.TouchTap(System.currentTimeMillis())

            BleGattProfile.BATTERY_CHARACTERISTIC ->
                GlassesEvent.BatteryUpdate(percent = value[0].toInt() and 0xFF)

            BleGattProfile.RADAR_GESTURE_CHARACTERISTIC ->
                RadarGestureType.entries.getOrNull(value[0].toInt())
                    ?.let { GlassesEvent.RadarGesture(it) }

            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    @Suppress("DEPRECATION") // BluetoothGattDescriptor#setValue has no replacement below API 33; minSdk is 26
    @SuppressLint("MissingPermission")
    private fun writeEnableNotification(
        g: BluetoothGatt,
        descriptor: android.bluetooth.BluetoothGattDescriptor
    ) {
        descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(descriptor)
    }
}

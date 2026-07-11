package com.pinch.gary.glasses.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.model.GlassesDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleScanner"

/** Thin wrapper around [android.bluetooth.le.BluetoothLeScanner], filtered to the glasses' advertised service. */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @SuppressLint("MissingPermission") // caller is required to have checked RequiredPermissions.ble first
    fun scanForGlasses(): Flow<GlassesDevice> = callbackFlow {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner

        if (scanner == null) {
            Logger.w(TAG, "BluetoothLeScanner unavailable (Bluetooth off or unsupported)")
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = GlassesDevice(
                    address = result.device.address,
                    name = result.scanRecord?.deviceName
                )
                trySend(device)
            }

            override fun onScanFailed(errorCode: Int) {
                Logger.e(TAG, "BLE scan failed with error code $errorCode")
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleGattProfile.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, callback)
        Logger.d(TAG, "BLE scan started")

        awaitClose {
            scanner.stopScan(callback)
            Logger.d(TAG, "BLE scan stopped")
        }
    }
}

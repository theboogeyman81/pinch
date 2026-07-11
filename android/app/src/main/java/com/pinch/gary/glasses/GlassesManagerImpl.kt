package com.pinch.gary.glasses

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.ble.BleConnectionManager
import com.pinch.gary.glasses.ble.BleScanner
import com.pinch.gary.glasses.ble.GattConnectionEvent
import com.pinch.gary.glasses.mjpeg.FrameRingBuffer
import com.pinch.gary.glasses.mjpeg.MjpegStreamClient
import com.pinch.gary.glasses.model.GlassesConnectionState
import com.pinch.gary.glasses.model.GlassesDevice
import com.pinch.gary.glasses.model.GlassesEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "GlassesManager"
private const val MAX_BACKOFF_SECONDS = 30L

@Singleton
class GlassesManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleScanner: BleScanner,
    private val bleConnectionManager: BleConnectionManager,
    private val mjpegStreamClient: MjpegStreamClient,
    private val frameRingBuffer: FrameRingBuffer,
    @ApplicationScope private val scope: CoroutineScope
) : GlassesManager {

    private val _connectionState = MutableStateFlow<GlassesConnectionState>(GlassesConnectionState.Disconnected)
    override val connectionState: StateFlow<GlassesConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GlassesEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<GlassesEvent> = _events.asSharedFlow()

    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectAttempt = 0

    override fun startScanning() {
        if (_connectionState.value is GlassesConnectionState.Scanning) return

        _connectionState.value = GlassesConnectionState.Scanning
        scanJob?.cancel()
        scanJob = scope.launch {
            bleScanner.scanForGlasses()
                .catch { t -> Logger.e(TAG, "Scan flow error", t) }
                .onEach { device ->
                    scanJob?.cancel()
                    connectToDevice(device)
                }
                .collect { }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: GlassesDevice) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            val bluetoothDevice = bluetoothManager?.adapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                Logger.e(TAG, "Could not resolve BluetoothDevice for ${device.address}")
                scheduleReconnect()
                return@launch
            }

            bleConnectionManager.connect(bluetoothDevice)
                .catch { t ->
                    Logger.e(TAG, "GATT connection error", t)
                    scheduleReconnect()
                }
                .collect { event ->
                    when (event) {
                        is GattConnectionEvent.Connected ->
                            _connectionState.value = GlassesConnectionState.BleConnected(device)

                        is GattConnectionEvent.ServicesReady -> {
                            reconnectAttempt = 0
                            _connectionState.value = GlassesConnectionState.BleConnected(device)
                            // TODO(hardware/): negotiate WiFi credentials + MJPEG URL over
                            // BleGattProfile.WIFI_CREDENTIALS_CHARACTERISTIC, then call
                            // startMjpegStream() once firmware exposes an endpoint.
                        }

                        is GattConnectionEvent.GlassesEventReceived ->
                            _events.emit(event.event)

                        is GattConnectionEvent.Disconnected -> {
                            frameRingBuffer.clear()
                            scheduleReconnect()
                        }
                    }
                }
        }
    }

    private fun startMjpegStream(streamUrl: String, device: GlassesDevice) {
        scope.launch {
            mjpegStreamClient.stream(streamUrl)
                .catch { t -> Logger.e(TAG, "MJPEG stream error", t) }
                .onEach { frame -> frameRingBuffer.push(frame) }
                .collect { }
        }
        _connectionState.value = GlassesConnectionState.Streaming(device)
    }

    private fun scheduleReconnect() {
        connectionJob?.cancel()
        reconnectAttempt += 1
        _connectionState.value = GlassesConnectionState.Reconnecting(reconnectAttempt)

        scope.launch {
            val backoffSeconds = min(MAX_BACKOFF_SECONDS, 2.0.pow(reconnectAttempt).toLong())
            Logger.d(TAG, "Reconnecting in ${backoffSeconds}s (attempt $reconnectAttempt)")
            delay(backoffSeconds * 1000)
            startScanning()
        }
    }

    override fun disconnect() {
        scanJob?.cancel()
        connectionJob?.cancel()
        bleConnectionManager.disconnect()
        frameRingBuffer.clear()
        reconnectAttempt = 0
        _connectionState.value = GlassesConnectionState.Disconnected
    }
}

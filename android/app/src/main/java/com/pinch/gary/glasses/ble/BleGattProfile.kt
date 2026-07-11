package com.pinch.gary.glasses.ble

import java.util.UUID

/**
 * GATT UUIDs for the ESP32-S3 glasses firmware.
 *
 * PLACEHOLDER — these are not real UUIDs. The firmware's GATT profile is
 * defined in hardware/ (not yet built, per CLAUDE.md milestone 4). Every
 * BLE call site reads UUIDs from here rather than inlining them, so once
 * hardware/ lands there is exactly one file to update.
 */
object BleGattProfile {
    val SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")

    val TOUCH_EVENT_CHARACTERISTIC: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
    val BATTERY_CHARACTERISTIC: UUID = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
    val RADAR_GESTURE_CHARACTERISTIC: UUID = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
    val LED_COMMAND_CHARACTERISTIC: UUID = UUID.fromString("0000fee4-0000-1000-8000-00805f9b34fb")
    val WIFI_CREDENTIALS_CHARACTERISTIC: UUID = UUID.fromString("0000fee5-0000-1000-8000-00805f9b34fb")
    val AUDIO_OUTPUT_CHARACTERISTIC: UUID = UUID.fromString("0000fee6-0000-1000-8000-00805f9b34fb")

    val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

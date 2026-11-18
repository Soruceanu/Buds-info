package com.example.model

enum class EarbudBrandPreset(val labelRu: String, val subtitleRu: String) {
  AUTO("Автоопределение", "Xiaomi BLE, Fast Pair и классический BT"),
  XIAOMI_REDMI("Xiaomi / Redmi Buds", "Протокол Mi BLE (0xFE95 / 0xFD2D / FastConnect)"),
  FAST_PAIR("Google Fast Pair", "Стандарт BLE Fast Pair (0xFE2C)"),
  APPLE_CLONE("AirPods / Универсальные TWS", "Proximity BLE (0x004C)"),
  UNIVERSAL_TWS("Стандартный Bluetooth", "HFP / GATT Battery Service (0x180F)"),
}

enum class BatteryProtocol(val displayNameRu: String) {
  XIAOMI_BLE("Xiaomi BLE"),
  FAST_PAIR_BLE("Google Fast Pair"),
  APPLE_PROXIMITY_BLE("TWS Proximity BLE"),
  GATT_BATTERY("BLE GATT 0x180F"),
  BLUETOOTH_CLASSIC("Bluetooth Classic"),
  PREVIEW_SAMPLE("Демо-предпросмотр шторки"),
  DISCONNECTED("Не подключены"),
}

data class TwsBatteryState(
  val deviceName: String = "Наушники не подключены",
  val deviceAddress: String? = null,
  val brandPreset: EarbudBrandPreset = EarbudBrandPreset.AUTO,
  val protocolSource: BatteryProtocol = BatteryProtocol.DISCONNECTED,
  val leftPercent: Int? = null,
  val rightPercent: Int? = null,
  val combinedPercent: Int? = null,
  val casePercent: Int? = null,
  val isLeftCharging: Boolean = false,
  val isRightCharging: Boolean = false,
  val isCombinedCharging: Boolean = false,
  val isCaseCharging: Boolean = false,
  val isLidOpen: Boolean? = null,
  val isConnected: Boolean = false,
  val isShadeWidgetActive: Boolean = false,
  val isPreviewMode: Boolean = false,
  val isScanningBle: Boolean = false,
  val rssiDbm: Int? = null,
  val lastUpdatedMillis: Long = 0L,
) {
  val hasAnyBatteryReading: Boolean
    get() = leftPercent != null || rightPercent != null || combinedPercent != null || casePercent != null

  val minimumBudBattery: Int?
    get() = listOfNotNull(leftPercent, rightPercent, combinedPercent).minOrNull()
}

data class PairedAudioDevice(
  val name: String,
  val address: String,
  val isConnected: Boolean,
  val detectedBrand: EarbudBrandPreset,
  val classicBatteryPercent: Int?,
)

data class AppSettings(
  val autoShowOnConnect: Boolean = true,
  val autoHideOnDisconnect: Boolean = true,
  val showCaseInCompact: Boolean = true,
  val lowBatteryAlertEnabled: Boolean = true,
  val lowBatteryThreshold: Int = 20,
  val useDarkTheme: Boolean = true,
  val preferredDeviceAddress: String? = null,
  val preferredBrandPreset: EarbudBrandPreset = EarbudBrandPreset.AUTO,
)

data class PermissionStatus(
  val hasBluetoothConnect: Boolean,
  val hasBluetoothScan: Boolean,
  val hasNotifications: Boolean,
  val hasLegacyLocationIfNeeded: Boolean,
) {
  val allCriticalGranted: Boolean
    get() = hasBluetoothConnect && hasBluetoothScan && hasNotifications && hasLegacyLocationIfNeeded
}

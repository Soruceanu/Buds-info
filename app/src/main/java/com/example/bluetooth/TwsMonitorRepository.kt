package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.data.SettingsRepository
import com.example.model.AppSettings
import com.example.model.BatteryProtocol
import com.example.model.EarbudBrandPreset
import com.example.model.PairedAudioDevice
import com.example.model.PermissionStatus
import com.example.model.TwsBatteryState
import com.example.notification.TwsNotificationHelper
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TwsMonitorRepository private constructor(private val appContext: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  val settingsRepository = SettingsRepository(appContext)

  private val bluetoothManager: BluetoothManager? =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _batteryState = MutableStateFlow(TwsBatteryState())
  val batteryState: StateFlow<TwsBatteryState> = _batteryState.asStateFlow()

  private val _pairedDevices = MutableStateFlow<List<PairedAudioDevice>>(emptyList())
  val pairedDevices: StateFlow<List<PairedAudioDevice>> = _pairedDevices.asStateFlow()

  private val _settings = MutableStateFlow(AppSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private var a2dpProxy: BluetoothProfile? = null
  private var headsetProxy: BluetoothProfile? = null
  private var activeGatt: BluetoothGatt? = null
  private var bleScanJob: Job? = null
  private var lowBatteryAlertSentForSession = false
  private var lastWakeRefreshMs = 0L

  private val profileListener =
    object : BluetoothProfile.ServiceListener {
      override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        if (profile == BluetoothProfile.A2DP) a2dpProxy = proxy
        if (profile == BluetoothProfile.HEADSET) headsetProxy = proxy
        refreshConnectedAudioDevices()
      }

      override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.A2DP) a2dpProxy = null
        if (profile == BluetoothProfile.HEADSET) headsetProxy = null
      }
    }

  private val bleScanCallback =
    object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        handleBleScanResult(result)
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach { handleBleScanResult(it) }
      }
    }

  init {
    scope.launch {
      settingsRepository.settingsFlow.collect { updated ->
        _settings.value = updated
        if (_batteryState.value.isShadeWidgetActive) {
          TwsNotificationHelper.showOrUpdateShadeWidget(appContext, _batteryState.value, updated)
        }
      }
    }
    initProfileProxies()
  }

  fun checkPermissions(): PermissionStatus {
    val hasConnect =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    val hasScan =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    val hasLegacyLoc =
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    val hasNotif = TwsNotificationHelper.hasNotificationPermission(appContext)
    return PermissionStatus(
      hasBluetoothConnect = hasConnect,
      hasBluetoothScan = hasScan,
      hasNotifications = hasNotif,
      hasLegacyLocationIfNeeded = hasLegacyLoc,
    )
  }

  fun initProfileProxies() {
    val adapter = bluetoothAdapter ?: return
    if (!checkPermissions().hasBluetoothConnect) return
    try {
      if (a2dpProxy == null) {
        adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
      }
      if (headsetProxy == null) {
        adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
      }
    } catch (_: SecurityException) {
      // Permission not yet granted
    }
  }

  fun refreshAllTelemetry() {
    initProfileProxies()
    refreshConnectedAudioDevices()
    startBleTelemetryScan(durationMs = 6_000L)
  }

  /**
   * Called on screen wake / user unlock (shade pull-down opportunity) with a 12s cooldown
   * so telemetry is fresh without wasting battery.
   */
  fun refreshOnWakeOrShadePull() {
    val now = System.currentTimeMillis()
    if (now - lastWakeRefreshMs < 12_000L) return
    lastWakeRefreshMs = now
    if (!_batteryState.value.isConnected) return
    refreshConnectedAudioDevices()
    startBleTelemetryScan(durationMs = 5_000L)
  }

  @SuppressLint("MissingPermission")
  fun refreshConnectedAudioDevices() {
    val adapter = bluetoothAdapter ?: return
    if (!checkPermissions().hasBluetoothConnect) {
      _pairedDevices.value = emptyList()
      return
    }

    val connectedSet = mutableSetOf<String>()
    try {
      a2dpProxy?.connectedDevices?.forEach { connectedSet.add(it.address) }
      headsetProxy?.connectedDevices?.forEach { connectedSet.add(it.address) }
    } catch (_: SecurityException) {
      return
    }

    val bonded =
      try {
        adapter.bondedDevices?.toList().orEmpty()
      } catch (_: SecurityException) {
        emptyList()
      }

    val pairedList =
      bonded
        .map { device ->
          val devName = safeDeviceName(device)
          val classicPct = readClassicBatteryLevel(device)
          val isConnected = connectedSet.contains(device.address) || isDeviceAclConnected(device)
          PairedAudioDevice(
            name = devName,
            address = device.address,
            isConnected = isConnected,
            detectedBrand = TwsBatteryParser.guessBrandPreset(devName),
            classicBatteryPercent = classicPct,
          )
        }
        .sortedByDescending { it.isConnected }

    _pairedDevices.value = pairedList

    val preferredAddr = _settings.value.preferredDeviceAddress
    val activeDevice =
      pairedList.firstOrNull { it.isConnected && it.address == preferredAddr }
        ?: pairedList.firstOrNull { it.isConnected }

    if (activeDevice != null) {
      val current = _batteryState.value
      val sameDevice = current.deviceAddress == activeDevice.address && current.isConnected
      val classicPct = TwsBatteryParser.quantizeToFive(activeDevice.classicBatteryPercent)

      val nextLeft = current.leftPercent ?: classicPct
      val nextRight = current.rightPercent ?: classicPct
      val nextProtocol =
        if (current.protocolSource == BatteryProtocol.DISCONNECTED) {
          BatteryProtocol.BLUETOOTH_CLASSIC
        } else {
          current.protocolSource
        }

      val updated =
        if (sameDevice) {
          if (current.deviceName == activeDevice.name &&
              current.leftPercent == nextLeft &&
              current.rightPercent == nextRight &&
              current.protocolSource == nextProtocol) {
            return
          }
          current.copy(
            deviceName = activeDevice.name,
            leftPercent = nextLeft,
            rightPercent = nextRight,
            protocolSource = nextProtocol,
            lastUpdatedMillis = System.currentTimeMillis(),
          )
        } else {
          lowBatteryAlertSentForSession = false
          TwsBatteryState(
            deviceName = activeDevice.name,
            deviceAddress = activeDevice.address,
            brandPreset =
              if (_settings.value.preferredBrandPreset == EarbudBrandPreset.AUTO) {
                activeDevice.detectedBrand
              } else {
                _settings.value.preferredBrandPreset
              },
            protocolSource = BatteryProtocol.BLUETOOTH_CLASSIC,
            leftPercent = classicPct,
            rightPercent = classicPct,
            casePercent = null,
            isConnected = true,
            isPreviewMode = false,
            isShadeWidgetActive = _settings.value.autoShowOnConnect,
            lastUpdatedMillis = System.currentTimeMillis(),
          )
        }
      _batteryState.value = updated
      if (updated.isShadeWidgetActive) {
        TwsNotificationHelper.showOrUpdateShadeWidget(appContext, updated, _settings.value)
      }
      if (!lowBatteryAlertSentForSession) {
        val minBud = updated.minimumBudBattery
        if (minBud != null && minBud <= _settings.value.lowBatteryThreshold) {
          lowBatteryAlertSentForSession = true
          TwsNotificationHelper.maybePostLowBatteryAlert(appContext, updated, _settings.value)
        }
      }
      // Also probe BLE GATT Battery Service if per-bud levels are still missing
      if (updated.casePercent == null) {
        bonded.firstOrNull { it.address == activeDevice.address }?.let { queryGattBattery(it) }
      }
    } else if (!_batteryState.value.isPreviewMode) {
      val wasConnected = _batteryState.value.isConnected
      if (wasConnected) {
        onDeviceDisconnected(null)
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun onDeviceConnected(device: BluetoothDevice?) {
    if (!checkPermissions().hasBluetoothConnect) return
    val name = device?.let { safeDeviceName(it) } ?: "Мои наушники"
    val address = device?.address
    val classicPct = TwsBatteryParser.quantizeToFive(device?.let { readClassicBatteryLevel(it) })
    val guessedBrand = TwsBatteryParser.guessBrandPreset(name)
    lowBatteryAlertSentForSession = false

    val next =
      TwsBatteryState(
        deviceName = name,
        deviceAddress = address,
        brandPreset =
          if (_settings.value.preferredBrandPreset == EarbudBrandPreset.AUTO) guessedBrand
          else _settings.value.preferredBrandPreset,
        protocolSource = BatteryProtocol.BLUETOOTH_CLASSIC,
        leftPercent = classicPct,
        rightPercent = classicPct,
        casePercent = null,
        isConnected = true,
        isPreviewMode = false,
        isShadeWidgetActive = _settings.value.autoShowOnConnect,
        lastUpdatedMillis = System.currentTimeMillis(),
      )
    _batteryState.value = next

    if (_settings.value.autoShowOnConnect) {
      TwsNotificationHelper.showOrUpdateShadeWidget(appContext, next, _settings.value)
    }

    refreshConnectedAudioDevices()
    startBleTelemetryScan()
    if (device != null) queryGattBattery(device)
  }

  fun onDeviceDisconnected(deviceAddress: String?) {
    val current = _batteryState.value
    if (current.isPreviewMode) return
    if (deviceAddress != null && current.deviceAddress != null && deviceAddress != current.deviceAddress) {
      return
    }
    lowBatteryAlertSentForSession = false
    closeGatt()
    stopBleTelemetryScan()

    val shouldHide = _settings.value.autoHideOnDisconnect
    _batteryState.value =
      TwsBatteryState(
        deviceName = "Наушники не подключены",
        isConnected = false,
        isShadeWidgetActive = !shouldHide && current.isShadeWidgetActive,
        protocolSource = BatteryProtocol.DISCONNECTED,
      )
    if (shouldHide) {
      TwsNotificationHelper.cancelShadeWidget(appContext)
    } else if (_batteryState.value.isShadeWidgetActive) {
      TwsNotificationHelper.showOrUpdateShadeWidget(appContext, _batteryState.value, _settings.value)
    }
    refreshConnectedAudioDevices()
  }

  fun onClassicBatteryBroadcast(device: BluetoothDevice?, level: Int) {
    if (level !in 0..100) return
    val current = _batteryState.value
    if (device != null && current.deviceAddress != null && device.address != current.deviceAddress) {
      return
    }
    val stepped = TwsBatteryParser.quantizeToFive(level) ?: return
    if (current.leftPercent == stepped && current.rightPercent == stepped && current.isConnected) {
      return
    }
    val updated =
      current.copy(
        leftPercent = current.leftPercent ?: stepped,
        rightPercent = current.rightPercent ?: stepped,
        isConnected = true,
        lastUpdatedMillis = System.currentTimeMillis(),
      )
    _batteryState.value = updated
    if (updated.isShadeWidgetActive) {
      TwsNotificationHelper.showOrUpdateShadeWidget(appContext, updated, _settings.value)
    }
  }

  fun onVendorAtEvent(device: BluetoothDevice?, command: String?, args: Array<Any>?) {
    if (command != "+IPHONEACCEV") return
    val parsed = TwsBatteryParser.parseIphoneAccEv(args) ?: return
    val current = _batteryState.value
    if (device != null && current.deviceAddress != null && device.address != current.deviceAddress) {
      return
    }
    applyParsedTelemetry(parsed, rssi = current.rssiDbm)
  }

  @SuppressLint("MissingPermission")
  fun startBleTelemetryScan(durationMs: Long = 5_000L) {
    val perms = checkPermissions()
    if (!perms.hasBluetoothScan || !perms.hasLegacyLocationIfNeeded) return
    val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

    bleScanJob?.cancel()
    _batteryState.update { it.copy(isScanningBle = true) }

    val filters =
      listOf(
        ScanFilter.Builder()
          .setServiceData(ParcelUuid(UUID.fromString(TwsBatteryParser.FAST_PAIR_UUID)), null)
          .build(),
        ScanFilter.Builder()
          .setServiceData(ParcelUuid(UUID.fromString(TwsBatteryParser.XIAOMI_FAST_CONNECT_UUID)), null)
          .build(),
        ScanFilter.Builder()
          .setServiceData(ParcelUuid(UUID.fromString(TwsBatteryParser.XIAOMI_MI_SERVICE_UUID)), null)
          .build(),
        ScanFilter.Builder()
          .setManufacturerData(TwsBatteryParser.XIAOMI_MANUFACTURER_ID, null)
          .build(),
        ScanFilter.Builder()
          .setManufacturerData(TwsBatteryParser.XIAOMI_ALT_MANUFACTURER_ID, null)
          .build(),
        ScanFilter.Builder()
          .setManufacturerData(TwsBatteryParser.APPLE_MANUFACTURER_ID, null)
          .build(),
      )

    val settings =
      ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
    try {
      scanner.startScan(filters, settings, bleScanCallback)
    } catch (_: SecurityException) {
      _batteryState.update { it.copy(isScanningBle = false) }
      return
    }

    bleScanJob =
      scope.launch {
        delay(durationMs)
        stopBleTelemetryScan()
      }
  }

  @SuppressLint("MissingPermission")
  fun stopBleTelemetryScan() {
    bleScanJob?.cancel()
    bleScanJob = null
    _batteryState.update { it.copy(isScanningBle = false) }
    val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
    if (!checkPermissions().hasBluetoothScan) return
    try {
      scanner.stopScan(bleScanCallback)
    } catch (_: Exception) {
      // Ignore if Bluetooth turned off
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleBleScanResult(result: ScanResult) {
    val record = result.scanRecord ?: return
    val current = _batteryState.value
    val resultName =
      try {
        record.deviceName ?: result.device?.name
      } catch (_: SecurityException) {
        record.deviceName
      }

    // Match against connected device if known, or accept Xiaomi/FastPair frames if actively paired
    val isSameAddress = current.deviceAddress != null && result.device?.address == current.deviceAddress
    val isKnownName =
      !resultName.isNullOrBlank() &&
        current.deviceName.contains(resultName, ignoreCase = true)

    var parsed: ParsedBatteryPayload? = null

    // 1. Xiaomi Service Data (0xFD2D / 0xFE95)
    record.getServiceData(android.os.ParcelUuid(UUID.fromString(TwsBatteryParser.XIAOMI_FAST_CONNECT_UUID)))
      ?.let { parsed = TwsBatteryParser.parseXiaomiBleData(it) }
    if (parsed == null) {
      record.getServiceData(android.os.ParcelUuid(UUID.fromString(TwsBatteryParser.XIAOMI_MI_SERVICE_UUID)))
        ?.let { parsed = TwsBatteryParser.parseXiaomiBleData(it) }
    }
    // 2. Xiaomi Manufacturer Data (0x038F / 0x0157)
    if (parsed == null) {
      record.getManufacturerSpecificData(TwsBatteryParser.XIAOMI_MANUFACTURER_ID)?.let {
        parsed = TwsBatteryParser.parseXiaomiBleData(it)
      }
    }
    if (parsed == null) {
      record.getManufacturerSpecificData(TwsBatteryParser.XIAOMI_ALT_MANUFACTURER_ID)?.let {
        parsed = TwsBatteryParser.parseXiaomiBleData(it)
      }
    }
    // 3. Google Fast Pair (0xFE2C)
    if (parsed == null) {
      record.getServiceData(android.os.ParcelUuid(UUID.fromString(TwsBatteryParser.FAST_PAIR_UUID)))
        ?.let { parsed = TwsBatteryParser.parseFastPairData(it) }
    }
    // 4. Apple / Universal Proximity BLE (0x004C)
    if (parsed == null && (isSameAddress || isKnownName || current.brandPreset == EarbudBrandPreset.APPLE_CLONE)) {
      record.getManufacturerSpecificData(TwsBatteryParser.APPLE_MANUFACTURER_ID)?.let {
        parsed = TwsBatteryParser.parseAppleProximityData(it)
      }
    }

    val validPayload = parsed ?: return
    if (current.isConnected && !isSameAddress && !isKnownName && current.deviceAddress != null) {
      // Redmi Buds / Fast Pair devices often rotate BLE RPA MAC addresses independently of Classic MAC;
      // enforce close RSSI proximity (-80 dBm) to ignore distant neighboring earbuds.
      if (result.rssi < -80) return
      val preset = current.brandPreset
      if (preset != EarbudBrandPreset.XIAOMI_REDMI &&
          preset != EarbudBrandPreset.FAST_PAIR &&
          preset != EarbudBrandPreset.AUTO) {
        return
      }
    }

    applyParsedTelemetry(validPayload, rssi = result.rssi)
  }

  private fun applyParsedTelemetry(payload: ParsedBatteryPayload, rssi: Int?) {
    val current = _batteryState.value
    val nextLeft = TwsBatteryParser.quantizeToFive(payload.leftPercent) ?: current.leftPercent
    val nextRight = TwsBatteryParser.quantizeToFive(payload.rightPercent) ?: current.rightPercent
    val nextCase = TwsBatteryParser.quantizeToFive(payload.casePercent) ?: current.casePercent

    val telemetryChanged =
      nextLeft != current.leftPercent ||
        nextRight != current.rightPercent ||
        nextCase != current.casePercent ||
        payload.isLeftCharging != current.isLeftCharging ||
        payload.isRightCharging != current.isRightCharging ||
        payload.isCaseCharging != current.isCaseCharging ||
        payload.protocol != current.protocolSource ||
        !current.isConnected

    if (!telemetryChanged) return

    val next =
      current.copy(
        leftPercent = nextLeft,
        rightPercent = nextRight,
        casePercent = nextCase,
        isLeftCharging = payload.isLeftCharging,
        isRightCharging = payload.isRightCharging,
        isCaseCharging = payload.isCaseCharging,
        isLidOpen = payload.isLidOpen ?: current.isLidOpen,
        protocolSource = payload.protocol,
        isConnected = true,
        isPreviewMode = false,
        rssiDbm = rssi,
        lastUpdatedMillis = System.currentTimeMillis(),
      )
    _batteryState.value = next
    if (next.isShadeWidgetActive) {
      TwsNotificationHelper.showOrUpdateShadeWidget(appContext, next, _settings.value)
    }
  }

  @SuppressLint("MissingPermission")
  private fun queryGattBattery(device: BluetoothDevice) {
    if (!checkPermissions().hasBluetoothConnect) return
    closeGatt()
    try {
      activeGatt =
        device.connectGatt(
          appContext,
          false,
          object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
              if (newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                  gatt.discoverServices()
                } catch (_: SecurityException) {
                  closeGatt()
                }
              } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt()
              }
            }

            private val batteryChars = ArrayDeque<BluetoothGattCharacteristic>()
            private val readings = mutableListOf<Int>()

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
              if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                return
              }
              val batUuid = UUID.fromString(TwsBatteryParser.BATTERY_SERVICE_UUID)
              val charUuid = UUID.fromString(TwsBatteryParser.BATTERY_LEVEL_CHAR_UUID)
              gatt.services
                ?.filter { it.uuid == batUuid }
                ?.forEach { service ->
                  service.characteristics
                    ?.filter { it.uuid == charUuid }
                    ?.forEach { batteryChars.addLast(it) }
                }
              readNextChar(gatt)
            }

            private fun readNextChar(gatt: BluetoothGatt) {
              val next = batteryChars.removeFirstOrNull()
              if (next == null) {
                if (readings.isNotEmpty()) {
                  val left = readings.getOrNull(0)
                  val right = readings.getOrNull(1) ?: left
                  val caseVal = readings.getOrNull(2)
                  scope.launch {
                    applyParsedTelemetry(
                      ParsedBatteryPayload(
                        leftPercent = left,
                        rightPercent = right,
                        casePercent = caseVal,
                        protocol = BatteryProtocol.GATT_BATTERY,
                      ),
                      rssi = _batteryState.value.rssiDbm,
                    )
                  }
                }
                closeGatt()
                return
              }
              try {
                gatt.readCharacteristic(next)
              } catch (_: SecurityException) {
                closeGatt()
              }
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicRead(
              gatt: BluetoothGatt,
              characteristic: BluetoothGattCharacteristic,
              status: Int,
            ) {
              if (status == BluetoothGatt.GATT_SUCCESS) {
                val pct = characteristic.value?.firstOrNull()?.toInt()?.and(0xFF)
                if (pct != null && pct in 0..100) {
                  readings.add(pct)
                }
              }
              readNextChar(gatt)
            }
          },
          BluetoothDevice.TRANSPORT_LE,
        )
    } catch (_: SecurityException) {
      closeGatt()
    }
  }

  @SuppressLint("MissingPermission")
  private fun closeGatt() {
    try {
      activeGatt?.close()
    } catch (_: Exception) {
      // Ignore
    } finally {
      activeGatt = null
    }
  }

  fun showShadeWidgetNow() {
    val current = _batteryState.value
    val targetState =
      if (current.isConnected) {
        current.copy(isShadeWidgetActive = true)
      } else {
        TwsBatteryState(
          deviceName = "Мои наушники",
          brandPreset = EarbudBrandPreset.AUTO,
          protocolSource = BatteryProtocol.PREVIEW_SAMPLE,
          leftPercent = 95,
          rightPercent = 90,
          casePercent = 65,
          isLeftCharging = false,
          isRightCharging = true,
          isCaseCharging = false,
          isConnected = false,
          isPreviewMode = true,
          isShadeWidgetActive = true,
          lastUpdatedMillis = System.currentTimeMillis(),
        )
      }
    _batteryState.value = targetState
    TwsNotificationHelper.showOrUpdateShadeWidget(
      appContext,
      targetState,
      _settings.value,
      forceUpdate = true,
    )
  }

  fun hideShadeWidget() {
    val current = _batteryState.value
    val next =
      if (current.isPreviewMode) {
        TwsBatteryState(isConnected = false, isShadeWidgetActive = false)
      } else {
        current.copy(isShadeWidgetActive = false)
      }
    _batteryState.value = next
    TwsNotificationHelper.cancelShadeWidget(appContext)
  }

  fun updatePreviewSampleLevels(
    left: Int,
    right: Int,
    casePct: Int,
    leftCharging: Boolean,
    rightCharging: Boolean,
    caseCharging: Boolean,
  ) {
    val current = _batteryState.value
    if (!current.isPreviewMode && current.isConnected) return
    val next =
      TwsBatteryState(
        deviceName = "Мои наушники",
        brandPreset = EarbudBrandPreset.AUTO,
        protocolSource = BatteryProtocol.PREVIEW_SAMPLE,
        leftPercent = TwsBatteryParser.quantizeToFive(left),
        rightPercent = TwsBatteryParser.quantizeToFive(right),
        casePercent = TwsBatteryParser.quantizeToFive(casePct),
        isLeftCharging = leftCharging,
        isRightCharging = rightCharging,
        isCaseCharging = caseCharging,
        isConnected = false,
        isPreviewMode = true,
        isShadeWidgetActive = current.isShadeWidgetActive,
        lastUpdatedMillis = System.currentTimeMillis(),
      )
    _batteryState.value = next
    if (next.isShadeWidgetActive) {
      TwsNotificationHelper.showOrUpdateShadeWidget(appContext, next, _settings.value)
    }
  }

  private fun safeDeviceName(device: BluetoothDevice): String {
    return try {
      device.name?.takeIf { it.isNotBlank() } ?: device.address ?: "TWS Наушники"
    } catch (_: SecurityException) {
      "TWS Наушники"
    }
  }

  private fun readClassicBatteryLevel(device: BluetoothDevice): Int? {
    return try {
      val method = device.javaClass.getMethod("getBatteryLevel")
      val value = method.invoke(device) as? Int
      if (value != null && value in 0..100) value else null
    } catch (_: Exception) {
      null
    }
  }

  private fun isDeviceAclConnected(device: BluetoothDevice): Boolean {
    return try {
      val method = device.javaClass.getMethod("isConnected")
      method.invoke(device) as? Boolean ?: false
    } catch (_: Exception) {
      false
    }
  }

  companion object {
    @Volatile private var instance: TwsMonitorRepository? = null

    fun getInstance(context: Context): TwsMonitorRepository {
      return instance
        ?: synchronized(this) {
          instance ?: TwsMonitorRepository(context.applicationContext).also { instance = it }
        }
    }
  }
}

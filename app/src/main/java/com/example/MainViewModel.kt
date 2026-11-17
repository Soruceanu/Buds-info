package com.example

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.TwsMonitorRepository
import com.example.bluetooth.TwsMonitorService
import com.example.model.AppSettings
import com.example.model.EarbudBrandPreset
import com.example.model.PairedAudioDevice
import com.example.model.PermissionStatus
import com.example.model.TwsBatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val titleRu: String) {
  MONITOR("Монитор"),
  DEVICES("Устройства"),
  SETTINGS("Настройки"),
}

data class MainUiState(
  val batteryState: TwsBatteryState = TwsBatteryState(),
  val pairedDevices: List<PairedAudioDevice> = emptyList(),
  val settings: AppSettings = AppSettings(),
  val permissions: PermissionStatus =
    PermissionStatus(
      hasBluetoothConnect = false,
      hasBluetoothScan = false,
      hasNotifications = false,
      hasLegacyLocationIfNeeded = true,
    ),
  val selectedTab: AppTab = AppTab.MONITOR,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = TwsMonitorRepository.getInstance(application)
  private val selectedTabFlow = MutableStateFlow(AppTab.MONITOR)
  private val permissionTick = MutableStateFlow(0)

  val uiState: StateFlow<MainUiState> =
    combine(
        repository.batteryState,
        repository.pairedDevices,
        repository.settings,
        selectedTabFlow,
        permissionTick,
      ) { battery, devices, settings, tab, _ ->
        MainUiState(
          batteryState = battery,
          pairedDevices = devices,
          settings = settings,
          permissions = repository.checkPermissions(),
          selectedTab = tab,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(permissions = repository.checkPermissions()),
      )

  init {
    refreshAll()
  }

  fun selectTab(tab: AppTab) {
    selectedTabFlow.value = tab
  }

  fun onPermissionsUpdated() {
    permissionTick.value += 1
    refreshAll()
  }

  fun refreshAll() {
    permissionTick.value += 1
    repository.refreshAllTelemetry()
    val state = repository.batteryState.value
    if (state.isConnected && repository.settings.value.autoShowOnConnect) {
      TwsMonitorService.startIfPermitted(getApplication())
    }
  }

  fun toggleShadeWidget() {
    val ctx = getApplication<Application>()
    if (repository.batteryState.value.isShadeWidgetActive) {
      repository.hideShadeWidget()
      ctx.stopService(Intent(ctx, TwsMonitorService::class.java))
    } else {
      repository.showShadeWidgetNow()
      if (repository.batteryState.value.isConnected) {
        TwsMonitorService.startIfPermitted(ctx)
      }
    }
  }

  fun updatePreviewLevels(
    left: Int,
    right: Int,
    casePct: Int,
    leftCharging: Boolean,
    rightCharging: Boolean,
    caseCharging: Boolean,
  ) {
    repository.updatePreviewSampleLevels(
      left = left,
      right = right,
      casePct = casePct,
      leftCharging = leftCharging,
      rightCharging = rightCharging,
      caseCharging = caseCharging,
    )
  }

  fun selectPreferredDevice(device: PairedAudioDevice) {
    viewModelScope.launch {
      repository.settingsRepository.setPreferredDevice(device.address, device.detectedBrand)
      repository.refreshAllTelemetry()
    }
  }

  fun setBrandPreset(preset: EarbudBrandPreset) {
    viewModelScope.launch {
      repository.settingsRepository.setPreferredBrandPreset(preset)
      repository.refreshAllTelemetry()
    }
  }

  fun setAutoShowOnConnect(enabled: Boolean) {
    viewModelScope.launch { repository.settingsRepository.setAutoShowOnConnect(enabled) }
  }

  fun setAutoHideOnDisconnect(enabled: Boolean) {
    viewModelScope.launch { repository.settingsRepository.setAutoHideOnDisconnect(enabled) }
  }

  fun setShowCaseInCompact(enabled: Boolean) {
    viewModelScope.launch { repository.settingsRepository.setShowCaseInCompact(enabled) }
  }

  fun setLowBatteryAlertEnabled(enabled: Boolean) {
    viewModelScope.launch { repository.settingsRepository.setLowBatteryAlertEnabled(enabled) }
  }

  fun setLowBatteryThreshold(threshold: Int) {
    viewModelScope.launch { repository.settingsRepository.setLowBatteryThreshold(threshold) }
  }

  fun setDarkTheme(dark: Boolean) {
    viewModelScope.launch { repository.settingsRepository.setUseDarkTheme(dark) }
  }

  fun requiredRuntimePermissions(): Array<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      list += Manifest.permission.BLUETOOTH_CONNECT
      list += Manifest.permission.BLUETOOTH_SCAN
    } else {
      list += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      list += Manifest.permission.POST_NOTIFICATIONS
    }
    return list.toTypedArray()
  }
}

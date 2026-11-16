package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.EarbudBrandPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "budspulse_settings")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val AUTO_SHOW_ON_CONNECT = booleanPreferencesKey("auto_show_on_connect")
    val AUTO_HIDE_ON_DISCONNECT = booleanPreferencesKey("auto_hide_on_disconnect")
    val SHOW_CASE_IN_COMPACT = booleanPreferencesKey("show_case_in_compact")
    val LOW_BATTERY_ALERT_ENABLED = booleanPreferencesKey("low_battery_alert_enabled")
    val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
    val USE_DARK_THEME = booleanPreferencesKey("use_dark_theme")
    val PREFERRED_DEVICE_ADDRESS = stringPreferencesKey("preferred_device_address")
    val PREFERRED_BRAND_PRESET = stringPreferencesKey("preferred_brand_preset")
  }

  val settingsFlow: Flow<AppSettings> =
    context.dataStore.data.map { prefs ->
      val presetName = prefs[Keys.PREFERRED_BRAND_PRESET]
      val preset =
        EarbudBrandPreset.entries.firstOrNull { it.name == presetName } ?: EarbudBrandPreset.AUTO
      AppSettings(
        autoShowOnConnect = prefs[Keys.AUTO_SHOW_ON_CONNECT] ?: true,
        autoHideOnDisconnect = prefs[Keys.AUTO_HIDE_ON_DISCONNECT] ?: true,
        showCaseInCompact = prefs[Keys.SHOW_CASE_IN_COMPACT] ?: true,
        lowBatteryAlertEnabled = prefs[Keys.LOW_BATTERY_ALERT_ENABLED] ?: true,
        lowBatteryThreshold = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20,
        useDarkTheme = prefs[Keys.USE_DARK_THEME] ?: true,
        preferredDeviceAddress = prefs[Keys.PREFERRED_DEVICE_ADDRESS],
        preferredBrandPreset = preset,
      )
    }

  suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

  suspend fun setAutoShowOnConnect(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_SHOW_ON_CONNECT] = enabled }
  }

  suspend fun setAutoHideOnDisconnect(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_HIDE_ON_DISCONNECT] = enabled }
  }

  suspend fun setShowCaseInCompact(enabled: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_CASE_IN_COMPACT] = enabled }
  }

  suspend fun setLowBatteryAlertEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.LOW_BATTERY_ALERT_ENABLED] = enabled }
  }

  suspend fun setLowBatteryThreshold(threshold: Int) {
    context.dataStore.edit { it[Keys.LOW_BATTERY_THRESHOLD] = threshold.coerceIn(5, 40) }
  }

  suspend fun setUseDarkTheme(dark: Boolean) {
    context.dataStore.edit { it[Keys.USE_DARK_THEME] = dark }
  }

  suspend fun setPreferredDevice(address: String?, preset: EarbudBrandPreset? = null) {
    context.dataStore.edit { prefs ->
      if (address == null) {
        prefs.remove(Keys.PREFERRED_DEVICE_ADDRESS)
      } else {
        prefs[Keys.PREFERRED_DEVICE_ADDRESS] = address
      }
      if (preset != null) {
        prefs[Keys.PREFERRED_BRAND_PRESET] = preset.name
      }
    }
  }

  suspend fun setPreferredBrandPreset(preset: EarbudBrandPreset) {
    context.dataStore.edit { it[Keys.PREFERRED_BRAND_PRESET] = preset.name }
  }
}

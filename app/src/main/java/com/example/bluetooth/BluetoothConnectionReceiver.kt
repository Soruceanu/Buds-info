package com.example.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notification.TwsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothConnectionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Main.immediate).launch {
      try {
        val repository = TwsMonitorRepository.getInstance(context)
        val settings = repository.settingsRepository.getCurrentSettings()
        val device = extractDevice(intent)

        when (intent.action) {
          BluetoothDevice.ACTION_ACL_CONNECTED -> {
            if (settings.autoShowOnConnect) {
              repository.onDeviceConnected(device)
              TwsMonitorService.startIfPermitted(context)
            }
          }
          BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
            repository.onDeviceDisconnected(device?.address)
            if (settings.autoHideOnDisconnect && !repository.batteryState.value.isConnected) {
              context.stopService(Intent(context, TwsMonitorService::class.java))
            }
          }
          BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
          BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
            val state =
              intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            if (state == BluetoothProfile.STATE_CONNECTED && settings.autoShowOnConnect) {
              repository.onDeviceConnected(device)
              TwsMonitorService.startIfPermitted(context)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
              repository.onDeviceDisconnected(device?.address)
              if (settings.autoHideOnDisconnect && !repository.batteryState.value.isConnected) {
                context.stopService(Intent(context, TwsMonitorService::class.java))
              }
            }
          }
          TwsMonitorService.ACTION_BATTERY_LEVEL_CHANGED -> {
            val level = intent.getIntExtra(TwsMonitorService.EXTRA_BATTERY_LEVEL, -1)
            repository.onClassicBatteryBroadcast(device, level)
          }
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (btState == BluetoothAdapter.STATE_OFF) {
              repository.onDeviceDisconnected(null)
              TwsNotificationHelper.cancelShadeWidget(context)
              context.stopService(Intent(context, TwsMonitorService::class.java))
            } else if (btState == BluetoothAdapter.STATE_ON && settings.autoShowOnConnect) {
              repository.refreshConnectedAudioDevices()
            }
          }
          Intent.ACTION_BOOT_COMPLETED -> {
            if (settings.autoShowOnConnect) {
              repository.refreshConnectedAudioDevices()
              if (repository.batteryState.value.isConnected) {
                TwsMonitorService.startIfPermitted(context)
              }
            }
          }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun extractDevice(intent: Intent): BluetoothDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
  }
}

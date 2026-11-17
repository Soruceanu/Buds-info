package com.example.bluetooth

import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.notification.TwsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TwsMonitorService : Service() {

  private lateinit var repository: TwsMonitorRepository
  private var receiverRegistered = false

  private val dynamicBtReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val device = extractBluetoothDevice(intent)
        when (intent.action) {
          BluetoothDevice.ACTION_ACL_CONNECTED -> {
            repository.onDeviceConnected(device)
          }
          BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
            repository.onDeviceDisconnected(device?.address)
            if (repository.settings.value.autoHideOnDisconnect && !repository.batteryState.value.isConnected) {
              stopSelfSafely()
            }
          }
          BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
          BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
            val state =
              intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            if (state == BluetoothProfile.STATE_CONNECTED) {
              repository.onDeviceConnected(device)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
              repository.refreshConnectedAudioDevices()
              if (repository.settings.value.autoHideOnDisconnect && !repository.batteryState.value.isConnected) {
                stopSelfSafely()
              }
            }
          }
          ACTION_BATTERY_LEVEL_CHANGED -> {
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
            repository.onClassicBatteryBroadcast(device, level)
          }
          BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
            val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
            @Suppress("UNCHECKED_CAST")
            val args =
              intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS)
                as? Array<Any>
            repository.onVendorAtEvent(device, cmd, args)
          }
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (btState == BluetoothAdapter.STATE_OFF) {
              repository.onDeviceDisconnected(null)
              stopSelfSafely()
            }
          }
          Intent.ACTION_SCREEN_ON,
          Intent.ACTION_USER_PRESENT -> {
            // Wake / unlock refresh so battery info is fresh when pulling down the notification shade
            repository.refreshOnWakeOrShadePull()
          }
          Intent.ACTION_SCREEN_OFF -> {
            // Stop active BLE scanning when screen turns off to save battery
            repository.stopBleTelemetryScan()
          }
        }
      }
    }

  private val serviceScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var refreshJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    isRunning = true
    repository = TwsMonitorRepository.getInstance(this)
    registerDynamicReceiver()
    startScreenOnClassicSync()
  }

  private fun startScreenOnClassicSync() {
    refreshJob?.cancel()
    refreshJob =
      serviceScope.launch {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        var tickCount = 0
        while (true) {
          delay(30_000L)
          if (powerManager?.isInteractive == true && repository.batteryState.value.isConnected) {
            tickCount += 1
            repository.refreshConnectedAudioDevices()
            if (tickCount % 2 == 0) {
              repository.startBleTelemetryScan(durationMs = 4_000L)
            }
          }
        }
      }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val state = repository.batteryState.value
    val settings = repository.settings.value
    val notification = TwsNotificationHelper.buildShadeWidgetNotification(this, state, settings)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && repository.checkPermissions().hasBluetoothConnect) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          startForeground(
            TwsNotificationHelper.WIDGET_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
          )
        } else {
          startForeground(TwsNotificationHelper.WIDGET_NOTIFICATION_ID, notification)
        }
        TwsNotificationHelper.markSnapshotPosted(state, settings)
      } catch (_: SecurityException) {
        TwsNotificationHelper.showOrUpdateShadeWidget(this, state, settings)
        stopSelf()
        return START_NOT_STICKY
      }
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      startForeground(TwsNotificationHelper.WIDGET_NOTIFICATION_ID, notification)
      TwsNotificationHelper.markSnapshotPosted(state, settings)
    } else {
      TwsNotificationHelper.showOrUpdateShadeWidget(this, state, settings)
      stopSelf()
      return START_NOT_STICKY
    }

    repository.refreshAllTelemetry()
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning = false
    refreshJob?.cancel()
    serviceScope.cancel()
    if (receiverRegistered) {
      try {
        unregisterReceiver(dynamicBtReceiver)
      } catch (_: Exception) {}
      receiverRegistered = false
    }
    repository.stopBleTelemetryScan()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun registerDynamicReceiver() {
    if (receiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        addAction(ACTION_BATTERY_LEVEL_CHANGED)
        addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_SCREEN_OFF)
        addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + ".76")
      }
    ContextCompat.registerReceiver(
      this,
      dynamicBtReceiver,
      filter,
      ContextCompat.RECEIVER_EXPORTED,
    )
    receiverRegistered = true
  }

  private fun stopSelfSafely() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION") stopForeground(true)
    }
    stopSelf()
  }

  @Suppress("DEPRECATION")
  private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
  }

  companion object {
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    @Volatile var isRunning: Boolean = false

    fun startIfPermitted(context: Context) {
      val repo = TwsMonitorRepository.getInstance(context)
      val perms = repo.checkPermissions()
      if (!perms.hasBluetoothConnect) {
        if (perms.hasNotifications && repo.batteryState.value.isShadeWidgetActive) {
          TwsNotificationHelper.showOrUpdateShadeWidget(
            context,
            repo.batteryState.value,
            repo.settings.value,
          )
        }
        return
      }
      val intent = Intent(context, TwsMonitorService::class.java)
      try {
        ContextCompat.startForegroundService(context, intent)
      } catch (_: Exception) {
        TwsNotificationHelper.showOrUpdateShadeWidget(
          context,
          repo.batteryState.value,
          repo.settings.value,
        )
      }
    }
  }
}

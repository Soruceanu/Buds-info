package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bluetooth.TwsMonitorRepository
import com.example.bluetooth.TwsMonitorService

class NotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val repository = TwsMonitorRepository.getInstance(context)
    when (intent.action) {
      TwsNotificationHelper.ACTION_REFRESH_TELEMETRY -> {
        repository.refreshAllTelemetry()
      }
      TwsNotificationHelper.ACTION_HIDE_WIDGET -> {
        repository.hideShadeWidget()
        context.stopService(Intent(context, TwsMonitorService::class.java))
      }
    }
  }
}

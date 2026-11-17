package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.bluetooth.TwsBatteryParser
import com.example.model.AppSettings
import com.example.model.TwsBatteryState

object TwsNotificationHelper {

  private const val LEGACY_CHANNEL_WIDGET_ID = "tws_battery_widget"
  const val CHANNEL_WIDGET_ID = "tws_battery_widget_min"
  const val CHANNEL_ALERT_ID = "tws_low_battery_alert"
  const val WIDGET_NOTIFICATION_ID = 1042
  private const val LOW_BATTERY_NOTIFICATION_ID = 1043

  const val ACTION_REFRESH_TELEMETRY = "com.example.action.REFRESH_TELEMETRY"
  const val ACTION_HIDE_WIDGET = "com.example.action.HIDE_WIDGET"

  private data class WidgetSnapshot(
    val leftPercent: Int?,
    val rightPercent: Int?,
    val casePercent: Int?,
    val isLeftCharging: Boolean,
    val isRightCharging: Boolean,
    val isCaseCharging: Boolean,
    val showCaseInCompact: Boolean,
  ) {
    companion object {
      fun from(state: TwsBatteryState, settings: AppSettings): WidgetSnapshot =
        WidgetSnapshot(
          leftPercent = TwsBatteryParser.quantizeToFive(state.leftPercent),
          rightPercent = TwsBatteryParser.quantizeToFive(state.rightPercent),
          casePercent = TwsBatteryParser.quantizeToFive(state.casePercent),
          isLeftCharging = state.isLeftCharging,
          isRightCharging = state.isRightCharging,
          isCaseCharging = state.isCaseCharging,
          showCaseInCompact = settings.showCaseInCompact,
        )
    }
  }

  @Volatile private var lastPostedSnapshot: WidgetSnapshot? = null

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.deleteNotificationChannel(LEGACY_CHANNEL_WIDGET_ID)

      val widgetChannel =
        NotificationChannel(
            CHANNEL_WIDGET_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
          )
          .apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          }
      manager.createNotificationChannel(widgetChannel)

      val alertChannel =
        NotificationChannel(
            CHANNEL_ALERT_ID,
            context.getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply { description = context.getString(R.string.notification_alert_channel_desc) }
      manager.createNotificationChannel(alertChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun buildShadeWidgetNotification(
    context: Context,
    state: TwsBatteryState,
    settings: AppSettings,
  ): Notification {
    ensureChannels(context)

    val openAppIntent =
      PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val snapshot = WidgetSnapshot.from(state, settings)
    val compactViews = buildCompactRemoteViews(context, snapshot)
    val expandedViews = buildExpandedRemoteViews(context, snapshot)
    val summaryText = formatSummaryLine(state)

    return NotificationCompat.Builder(context, CHANNEL_WIDGET_ID)
      .setSmallIcon(R.drawable.ic_earbuds_notif)
      .setContentTitle(summaryText)
      .setContentText(summaryText)
      .setContentIntent(openAppIntent)
      .setCustomContentView(compactViews)
      .setCustomBigContentView(expandedViews)
      .setShowWhen(false)
      .setWhen(0L)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .build()
  }

  fun markSnapshotPosted(state: TwsBatteryState, settings: AppSettings) {
    lastPostedSnapshot = WidgetSnapshot.from(state, settings)
  }

  fun showOrUpdateShadeWidget(
    context: Context,
    state: TwsBatteryState,
    settings: AppSettings,
    forceUpdate: Boolean = false,
  ): Boolean {
    if (!hasNotificationPermission(context)) return false
    val snapshot = WidgetSnapshot.from(state, settings)
    if (!forceUpdate && snapshot == lastPostedSnapshot) {
      return true
    }
    val notification = buildShadeWidgetNotification(context, state, settings)
    return try {
      NotificationManagerCompat.from(context).notify(WIDGET_NOTIFICATION_ID, notification)
      lastPostedSnapshot = snapshot
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun cancelShadeWidget(context: Context) {
    lastPostedSnapshot = null
    NotificationManagerCompat.from(context).cancel(WIDGET_NOTIFICATION_ID)
  }

  fun maybePostLowBatteryAlert(
    context: Context,
    state: TwsBatteryState,
    settings: AppSettings,
  ) {
    if (!settings.lowBatteryAlertEnabled || !state.isConnected || state.isPreviewMode) return
    if (!hasNotificationPermission(context)) return

    val minBud = state.minimumBudBattery ?: return
    if (minBud > settings.lowBatteryThreshold) return
    if (state.isLeftCharging && state.isRightCharging) return

    ensureChannels(context)
    val openIntent =
      PendingIntent.getActivity(
        context,
        20,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val alert =
      NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
        .setSmallIcon(R.drawable.ic_earbuds_notif)
        .setContentTitle("Низкий заряд: ${state.deviceName}")
        .setContentText(formatSummaryLine(state))
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build()

    try {
      NotificationManagerCompat.from(context).notify(LOW_BATTERY_NOTIFICATION_ID, alert)
    } catch (_: SecurityException) {
      // Ignore if notification permission was revoked
    }
  }

  fun formatSummaryLine(state: TwsBatteryState): String {
    val parts = mutableListOf<String>()
    TwsBatteryParser.quantizeToFive(state.leftPercent)?.let { parts += "Л: $it%" }
    TwsBatteryParser.quantizeToFive(state.rightPercent)?.let { parts += "П: $it%" }
    TwsBatteryParser.quantizeToFive(state.casePercent)?.let { parts += "К: $it%" }
    return if (parts.isEmpty()) "Подключены" else parts.joinToString("  ")
  }

  private fun buildCompactRemoteViews(
    context: Context,
    snapshot: WidgetSnapshot,
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.notification_tws_compact)
    val isDark = isSystemDarkTheme(context)

    bindSlot(
      views = views,
      containerId = R.id.compact_left_container,
      textId = R.id.compact_left_text,
      boltId = R.id.compact_left_bolt,
      prefix = "Л",
      percent = snapshot.leftPercent,
      isCharging = snapshot.isLeftCharging,
      isDark = isDark,
    )

    bindSlot(
      views = views,
      containerId = R.id.compact_right_container,
      textId = R.id.compact_right_text,
      boltId = R.id.compact_right_bolt,
      prefix = "П",
      percent = snapshot.rightPercent,
      isCharging = snapshot.isRightCharging,
      isDark = isDark,
    )

    bindSlot(
      views = views,
      containerId = R.id.compact_case_container,
      textId = R.id.compact_case_text,
      boltId = R.id.compact_case_bolt,
      prefix = "К",
      percent = if (snapshot.showCaseInCompact) snapshot.casePercent else null,
      isCharging = snapshot.isCaseCharging,
      isDark = isDark,
    )

    return views
  }

  private fun buildExpandedRemoteViews(
    context: Context,
    snapshot: WidgetSnapshot,
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.notification_tws_expanded)
    val isDark = isSystemDarkTheme(context)

    bindSlot(
      views = views,
      containerId = R.id.expanded_left_container,
      textId = R.id.expanded_left_pct,
      boltId = R.id.left_charging_bolt,
      prefix = "Л",
      percent = snapshot.leftPercent,
      isCharging = snapshot.isLeftCharging,
      isDark = isDark,
    )

    bindSlot(
      views = views,
      containerId = R.id.expanded_right_container,
      textId = R.id.expanded_right_pct,
      boltId = R.id.right_charging_bolt,
      prefix = "П",
      percent = snapshot.rightPercent,
      isCharging = snapshot.isRightCharging,
      isDark = isDark,
    )

    bindSlot(
      views = views,
      containerId = R.id.expanded_case_container,
      textId = R.id.expanded_case_pct,
      boltId = R.id.case_charging_bolt,
      prefix = "К",
      percent = snapshot.casePercent,
      isCharging = snapshot.isCaseCharging,
      isDark = isDark,
    )

    return views
  }

  private fun isSystemDarkTheme(context: Context): Boolean {
    val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMask == Configuration.UI_MODE_NIGHT_YES
  }

  private fun bindSlot(
    views: RemoteViews,
    containerId: Int,
    textId: Int,
    boltId: Int,
    prefix: String,
    percent: Int?,
    isCharging: Boolean,
    isDark: Boolean,
  ) {
    if (percent == null) {
      views.setViewVisibility(containerId, View.GONE)
      return
    }
    views.setViewVisibility(containerId, View.VISIBLE)
    views.setTextViewText(textId, "$prefix: $percent%")

    val textColor =
      when {
        isCharging -> if (isDark) 0xFFF8FAFC.toInt() else 0xFF111827.toInt()
        percent <= 20 -> if (isDark) 0xFFFF5263.toInt() else 0xFFDC2626.toInt()
        percent <= 45 -> if (isDark) 0xFFFF8A3D.toInt() else 0xFFEA580C.toInt()
        else -> if (isDark) 0xFFF8FAFC.toInt() else 0xFF111827.toInt()
      }
    views.setTextColor(textId, textColor)
    views.setViewVisibility(boltId, if (isCharging) View.VISIBLE else View.GONE)
  }
}

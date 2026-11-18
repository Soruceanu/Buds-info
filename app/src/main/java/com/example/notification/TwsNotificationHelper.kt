package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
    val titleText = buildStyledTitle(snapshot)

    return NotificationCompat.Builder(context, CHANNEL_WIDGET_ID)
      .setSmallIcon(R.drawable.ic_earbuds_notif)
      .setContentTitle(titleText)
      .setContentIntent(openAppIntent)
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

  private fun buildStyledTitle(snapshot: WidgetSnapshot): CharSequence {
    val builder = SpannableStringBuilder()
    appendSlot(builder, "Л", snapshot.leftPercent, snapshot.isLeftCharging)
    appendSlot(builder, "П", snapshot.rightPercent, snapshot.isRightCharging)
    if (snapshot.showCaseInCompact) {
      appendSlot(builder, "К", snapshot.casePercent, snapshot.isCaseCharging)
    }
    if (builder.isEmpty()) {
      builder.append("Подключены")
      builder.setSpan(
        StyleSpan(Typeface.BOLD),
        0,
        builder.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
    return builder
  }

  private fun appendSlot(
    builder: SpannableStringBuilder,
    prefix: String,
    percent: Int?,
    isCharging: Boolean,
  ) {
    if (percent == null) return
    if (builder.isNotEmpty()) {
      builder.append("   ")
    }
    if (isCharging) {
      val boltStart = builder.length
      builder.append("\u26A1\uFE0E")
      builder.setSpan(
        ForegroundColorSpan(0xFF10B981.toInt()),
        boltStart,
        builder.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
    val textStart = builder.length
    builder.append("$prefix: $percent%")
    builder.setSpan(
      StyleSpan(Typeface.BOLD),
      textStart,
      builder.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    if (!isCharging) {
      val tint =
        when {
          percent <= 20 -> 0xFFEF4444.toInt()
          percent <= 45 -> 0xFFF97316.toInt()
          else -> null
        }
      if (tint != null) {
        builder.setSpan(
          ForegroundColorSpan(tint),
          textStart,
          builder.length,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }
  }
}

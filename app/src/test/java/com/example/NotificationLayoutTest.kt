package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppSettings
import com.example.model.BatteryProtocol
import com.example.model.TwsBatteryState
import com.example.notification.TwsNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationLayoutTest {

  @Test
  fun `shade widget renders single-line non-expandable compact notification hiding closed case`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val state =
      TwsBatteryState(
        deviceName = "My Custom Buds",
        protocolSource = BatteryProtocol.BLUETOOTH_CLASSIC,
        leftPercent = null, // inside closed case
        rightPercent = 92,  // active in ear
        casePercent = null, // closed case
        isLeftCharging = false,
        isRightCharging = false,
        isCaseCharging = false,
        isConnected = true,
        isShadeWidgetActive = true,
      )
    val settings = AppSettings(showCaseInCompact = true)

    assertEquals("П: 90%", TwsNotificationHelper.formatSummaryLine(state))

    val notification = TwsNotificationHelper.buildShadeWidgetNotification(context, state, settings)
    assertNull(notification.contentView)
    assertNull(notification.bigContentView)
    assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    assertEquals(false, notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
    assertEquals("П: 90%", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(TwsNotificationHelper.CHANNEL_WIDGET_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)

    // Open case with left bud charging and right bud in ear
    val openCaseState =
      state.copy(
        leftPercent = 98,
        isLeftCharging = true,
        casePercent = 72,
        isLidOpen = true,
      )
    val openNotification =
      TwsNotificationHelper.buildShadeWidgetNotification(context, openCaseState, settings)
    val openTitle = openNotification.extras.getCharSequence(Notification.EXTRA_TITLE)
    assertNotNull(openTitle)
    assertEquals("\u26A1\uFE0EЛ: 100%   П: 90%   К: 70%", openTitle.toString())
    val spans = (openTitle as Spanned).getSpans(0, openTitle.length, ForegroundColorSpan::class.java)
    assertTrue(spans.any { it.foregroundColor == 0xFF10B981.toInt() })
  }
}

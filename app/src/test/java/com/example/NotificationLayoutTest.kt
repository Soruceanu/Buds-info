package com.example

import android.app.NotificationManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppSettings
import com.example.model.BatteryProtocol
import com.example.model.TwsBatteryState
import com.example.notification.TwsNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationLayoutTest {

  @Test
  fun `shade widget centers Л П К without icon or title and hides status bar icon via IMPORTANCE_MIN`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val state =
      TwsBatteryState(
        deviceName = "My Custom Buds",
        protocolSource = BatteryProtocol.XIAOMI_BLE,
        leftPercent = 99,
        rightPercent = 92,
        casePercent = 68,
        isLeftCharging = false,
        isRightCharging = true,
        isCaseCharging = false,
        isConnected = true,
        isShadeWidgetActive = true,
      )
    val settings = AppSettings(showCaseInCompact = true)

    assertEquals("Л: 100%  П: 90%  К: 70%", TwsNotificationHelper.formatSummaryLine(state))

    val notification = TwsNotificationHelper.buildShadeWidgetNotification(context, state, settings)
    assertNotNull(notification.contentView)
    assertNotNull(notification.bigContentView)
    assertEquals(false, notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_WHEN, true))

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(TwsNotificationHelper.CHANNEL_WIDGET_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)

    val parent = FrameLayout(context)
    val compactView = notification.contentView.apply(context, parent)
    assertEquals("Л: 100%", compactView.findViewById<TextView>(R.id.compact_left_text).text.toString())
    assertEquals("П: 90%", compactView.findViewById<TextView>(R.id.compact_right_text).text.toString())
    assertEquals("К: 70%", compactView.findViewById<TextView>(R.id.compact_case_text).text.toString())
    assertEquals(View.GONE, compactView.findViewById<View>(R.id.compact_left_bolt).visibility)
    assertEquals(View.VISIBLE, compactView.findViewById<View>(R.id.compact_right_bolt).visibility)
    assertEquals(View.GONE, compactView.findViewById<View>(R.id.compact_case_bolt).visibility)

    val expandedView = notification.bigContentView.apply(context, parent)
    assertEquals("Л: 100%", expandedView.findViewById<TextView>(R.id.expanded_left_pct).text.toString())
    assertEquals("П: 90%", expandedView.findViewById<TextView>(R.id.expanded_right_pct).text.toString())
    assertEquals("К: 70%", expandedView.findViewById<TextView>(R.id.expanded_case_pct).text.toString())
    assertEquals(View.GONE, expandedView.findViewById<View>(R.id.left_charging_bolt).visibility)
    assertEquals(View.VISIBLE, expandedView.findViewById<View>(R.id.right_charging_bolt).visibility)
    assertEquals(View.GONE, expandedView.findViewById<View>(R.id.case_charging_bolt).visibility)
  }
}

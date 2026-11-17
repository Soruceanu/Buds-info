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
  fun `shade widget hides closed case and bud in closed case while showing active buds`() {
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
    assertNotNull(notification.contentView)
    assertNotNull(notification.bigContentView)
    assertEquals(false, notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_WHEN, true))

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(TwsNotificationHelper.CHANNEL_WIDGET_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)

    val parent = FrameLayout(context)
    val compactView = notification.contentView.apply(context, parent)
    assertEquals(View.GONE, compactView.findViewById<View>(R.id.compact_left_container).visibility)
    assertEquals(View.VISIBLE, compactView.findViewById<View>(R.id.compact_right_container).visibility)
    assertEquals(View.GONE, compactView.findViewById<View>(R.id.compact_case_container).visibility)
    assertEquals("П: 90%", compactView.findViewById<TextView>(R.id.compact_right_text).text.toString())

    val expandedView = notification.bigContentView.apply(context, parent)
    assertEquals(View.GONE, expandedView.findViewById<View>(R.id.expanded_left_container).visibility)
    assertEquals(View.VISIBLE, expandedView.findViewById<View>(R.id.expanded_right_container).visibility)
    assertEquals(View.GONE, expandedView.findViewById<View>(R.id.expanded_case_container).visibility)
    assertEquals("П: 90%", expandedView.findViewById<TextView>(R.id.expanded_right_pct).text.toString())
  }
}

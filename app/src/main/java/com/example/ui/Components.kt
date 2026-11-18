package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppSettings
import com.example.model.TwsBatteryState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.MintElectric
import com.example.ui.theme.XiaomiCoral

@Composable
fun batterySemanticColor(percent: Int?, isCharging: Boolean): Color {
  return when {
    isCharging -> MintElectric
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    percent <= 20 -> AlertRed
    percent <= 45 -> XiaomiCoral
    else -> MintElectric
  }
}

@Composable
fun BatteryArcGauge(
  label: String,
  shortBadge: String,
  percent: Int?,
  isCharging: Boolean,
  modifier: Modifier = Modifier,
) {
  val targetFraction = ((percent ?: 0).coerceIn(0, 100)) / 100f
  val animatedSweep by
    animateFloatAsState(
      targetValue = targetFraction * 260f,
      animationSpec = tween(durationMillis = 550),
      label = "battery_sweep",
    )
  val accentColor = batterySemanticColor(percent, isCharging)
  val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.14f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isCharging) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = "Зарядка",
              tint = MintElectric,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
          }
          Text(
            text = shortBadge,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(78.dp)) {
          val strokeWidth = 8.dp.toPx()
          val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
          val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

          drawArc(
            color = trackColor,
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )

          if (percent != null && animatedSweep > 0f) {
            drawArc(
              color = accentColor,
              startAngle = 140f,
              sweepAngle = animatedSweep,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (isCharging) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = "Зарядка",
              tint = CyanSignal,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Visual preview of the ultra-slim single-row notification shade widget:
 * Left-aligned Л, П, К under the notification header, showing only active components.
 */
@Composable
fun MinimalistShadeWidgetPreview(
  state: TwsBatteryState,
  settings: AppSettings,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = Color(0xFF2B2D31),
    border = BorderStroke(1.dp, Color(0xFF3E4147)),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(start = 52.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val leftVal = if (state.isConnected || state.isPreviewMode) state.leftPercent else 95
      val rightVal = if (state.isConnected || state.isPreviewMode) state.rightPercent else 90
      val combinedVal = if (state.isConnected || state.isPreviewMode) state.combinedPercent else null
      val caseVal = if (state.isConnected || state.isPreviewMode) state.casePercent else null
      val leftChg = if (state.isConnected || state.isPreviewMode) state.isLeftCharging else false
      val rightChg = if (state.isConnected || state.isPreviewMode) state.isRightCharging else false
      val combinedChg = if (state.isConnected || state.isPreviewMode) state.isCombinedCharging else false
      val caseChg = if (state.isConnected || state.isPreviewMode) state.isCaseCharging else false

      if (leftVal != null) ShadeWidgetTextSlot("Л", leftVal, leftChg)
      if (rightVal != null) ShadeWidgetTextSlot("П", rightVal, rightChg)
      if (leftVal == null && rightVal == null && combinedVal != null) {
        ShadeWidgetTextSlot("Заряд", combinedVal, combinedChg)
      }
      if (settings.showCaseInCompact && caseVal != null) {
        ShadeWidgetTextSlot("К", caseVal, caseChg)
      }
    }
  }
}

@Composable
private fun ShadeWidgetTextSlot(
  prefix: String,
  percent: Int?,
  isCharging: Boolean,
) {
  val textColor =
    when {
      isCharging -> Color(0xFFF8FAFC)
      percent == null -> Color(0xFF94A3B8)
      percent <= 20 -> AlertRed
      percent <= 45 -> XiaomiCoral
      else -> Color(0xFFF8FAFC)
    }

  Row(verticalAlignment = Alignment.CenterVertically) {
    if (isCharging) {
      Icon(
        imageVector = Icons.Rounded.Bolt,
        contentDescription = null,
        tint = MintElectric,
        modifier = Modifier.size(12.dp),
      )
      Spacer(modifier = Modifier.width(1.dp))
    }
    Text(
      text = if (percent != null) "$prefix: $percent%" else "$prefix: —",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = textColor,
      maxLines = 1,
    )
  }
}

@Composable
fun PermissionStatusBanner(
  missingConnect: Boolean,
  missingNotifications: Boolean,
  onGrantClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (missingConnect) Icons.Rounded.Security else Icons.Rounded.NotificationsActive,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Требуются разрешения",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text =
          buildString {
            append("Чтобы виджет включался автоматически при подключении наушников и отображался в шторке уведомлений, разрешите доступ к ")
            if (missingConnect) append("Bluetooth-устройствам поблизости")
            if (missingConnect && missingNotifications) append(" и ")
            if (missingNotifications) append("уведомлениям")
            append(".")
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(
        onClick = onGrantClick,
        modifier = Modifier.fillMaxWidth().testTag("grant_permissions_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
      ) {
        Text("Выдать разрешения", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

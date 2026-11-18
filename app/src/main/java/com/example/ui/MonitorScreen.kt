package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.MainUiState
import com.example.R
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.MintElectric

@Composable
fun MonitorScreen(
  uiState: MainUiState,
  onRequestPermissions: () -> Unit,
  onRefreshTelemetry: () -> Unit,
  onToggleShadeWidget: () -> Unit,
  onToggleAutoShow: (Boolean) -> Unit,
  onUpdatePreviewLevels: (Int, Int, Int, Boolean, Boolean, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val battery = uiState.batteryState
  val perms = uiState.permissions

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (!perms.allCriticalGranted) {
      item {
        PermissionStatusBanner(
          missingConnect = !perms.hasBluetoothConnect || !perms.hasBluetoothScan || !perms.hasLegacyLocationIfNeeded,
          missingNotifications = !perms.hasNotifications,
          onGrantClick = onRequestPermissions,
        )
      }
    }

    // Hero Earbuds Card
    item {
      Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
      ) {
        Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
          Image(
            painter = painterResource(id = R.drawable.img_tws_hero),
            contentDescription = stringResource(R.string.cd_hero_earbuds),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors =
                      listOf(
                        Color(0x330A0D14),
                        Color(0xCC0A0D14),
                      ),
                  ),
                ),
          )

          Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(50),
              color =
                if (battery.isConnected) MintElectric.copy(alpha = 0.22f)
                else Color(0x991E293B),
              border =
                BorderStroke(
                  1.dp,
                  if (battery.isConnected) MintElectric.copy(alpha = 0.7f)
                  else Color(0x5594A3B8),
                ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector =
                    if (battery.isScanningBle) Icons.Rounded.BluetoothSearching
                    else Icons.Rounded.BluetoothAudio,
                  contentDescription = stringResource(R.string.cd_bluetooth_status),
                  tint = if (battery.isConnected) MintElectric else Color(0xFFCBD5E1),
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text =
                    when {
                      battery.isScanningBle -> "Поиск BLE..."
                      battery.isConnected -> battery.protocolSource.displayNameRu
                      battery.isPreviewMode -> "Предпросмотр виджета"
                      else -> "Ожидание подключения"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFF8FAFC),
                )
              }
            }

            IconButton(
              onClick = onRefreshTelemetry,
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .background(Color(0x88141923))
                  .testTag("refresh_telemetry_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_telemetry),
                tint = Color(0xFFF8FAFC),
              )
            }
          }

          Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
          ) {
            Text(
              text = battery.deviceName,
              style = MaterialTheme.typography.headlineMedium,
              color = Color(0xFFF8FAFC),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitle =
              when {
                battery.isConnected &&
                  battery.leftPercent == null &&
                  battery.rightPercent == null &&
                  battery.combinedPercent != null ->
                  "Заряд: ${battery.combinedPercent}% • Общий канал Bluetooth"
                battery.isConnected && battery.rssiDbm != null ->
                  "Сигнал ${battery.rssiDbm} dBm • Автовиджет активен"
                battery.isConnected -> "Подключено • Телеметрия обновляется"
                battery.isPreviewMode -> "Тестовые уровни отображаются в шторке"
                else -> "Подключите Xiaomi / TWS наушники — виджет появится сам"
              }
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFCBD5E1),
            )
          }
        }
      }
    }

    // 3-Column Visual Battery Gauges (Left, Right, Case)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BatteryArcGauge(
          label = "Левый",
          shortBadge = "Л",
          percent = battery.leftPercent,
          isCharging = battery.isLeftCharging,
          modifier = Modifier.weight(1f).testTag("gauge_left"),
        )
        BatteryArcGauge(
          label = "Правый",
          shortBadge = "П",
          percent = battery.rightPercent,
          isCharging = battery.isRightCharging,
          modifier = Modifier.weight(1f).testTag("gauge_right"),
        )
        BatteryArcGauge(
          label = "Кейс",
          shortBadge = "К",
          percent = battery.casePercent,
          isCharging = battery.isCaseCharging,
          modifier = Modifier.weight(1f).testTag("gauge_case"),
        )
      }
    }

    // Notification Shade Minimalist Single-Row Widget Card & Controls
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Виджет в шторке",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            Surface(
              shape = RoundedCornerShape(50),
              color =
                if (battery.isShadeWidgetActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = if (battery.isShadeWidgetActive) "В шторке" else "Скрыт",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (battery.isShadeWidgetActive) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Тонкая строка на едином фоне с шагом 5% — обновляется только при изменении показателя или открытии шторки:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Live Replica of the actual RemoteViews single-row widget
          MinimalistShadeWidgetPreview(
            state = battery,
            settings = uiState.settings,
          )

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (battery.isShadeWidgetActive) {
              FilledTonalButton(
                onClick = onToggleShadeWidget,
                modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_shade_widget_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsOff,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Скрыть из шторки")
              }
            } else {
              Button(
                onClick = onToggleShadeWidget,
                modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_shade_widget_button"),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              ) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  if (battery.isConnected) "Показать в шторке" else "Тест в шторке",
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Автозапуск при подключении",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = "Включает виджет в шторке как только наушники подключаются по Bluetooth",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = uiState.settings.autoShowOnConnect,
              onCheckedChange = onToggleAutoShow,
              modifier = Modifier.testTag("auto_launch_switch"),
            )
          }
        }
      }
    }

    // Preview Sliders when in Preview Mode so user can test visual color thresholds in real Android shade
    item {
      AnimatedVisibility(visible = battery.isPreviewMode) {
        var leftSample by remember(battery.leftPercent) { mutableIntStateOf(battery.leftPercent ?: 95) }
        var rightSample by remember(battery.rightPercent) { mutableIntStateOf(battery.rightPercent ?: 90) }
        var caseSample by remember(battery.casePercent) { mutableIntStateOf(battery.casePercent ?: 65) }
        var leftCharging by remember(battery.isLeftCharging) { mutableStateOf(battery.isLeftCharging) }
        var rightCharging by remember(battery.isRightCharging) { mutableStateOf(battery.isRightCharging) }
        var caseCharging by remember(battery.isCaseCharging) { mutableStateOf(battery.isCaseCharging) }

        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Настройка демо-заряда (шаг 5%)",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Левый (Л): $leftSample%", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = leftSample.toFloat(),
              onValueChange = {
                val snapped = (((it.toInt() + 2) / 5) * 5).coerceIn(0, 100)
                if (snapped != leftSample) {
                  leftSample = snapped
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                }
              },
              steps = 19,
              valueRange = 0f..100f,
            )
            Text("Правый (П): $rightSample%", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = rightSample.toFloat(),
              onValueChange = {
                val snapped = (((it.toInt() + 2) / 5) * 5).coerceIn(0, 100)
                if (snapped != rightSample) {
                  rightSample = snapped
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                }
              },
              steps = 19,
              valueRange = 0f..100f,
            )
            Text("Кейс (К): $caseSample%", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = caseSample.toFloat(),
              onValueChange = {
                val snapped = (((it.toInt() + 2) / 5) * 5).coerceIn(0, 100)
                if (snapped != caseSample) {
                  caseSample = snapped
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                }
              },
              steps = 19,
              valueRange = 0f..100f,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Индикатор зарядки (⚡ перед буквой):", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              androidx.compose.material3.FilterChip(
                selected = leftCharging,
                onClick = {
                  leftCharging = !leftCharging
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                },
                label = { Text("⚡ Л") },
              )
              androidx.compose.material3.FilterChip(
                selected = rightCharging,
                onClick = {
                  rightCharging = !rightCharging
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                },
                label = { Text("⚡ П") },
              )
              androidx.compose.material3.FilterChip(
                selected = caseCharging,
                onClick = {
                  caseCharging = !caseCharging
                  onUpdatePreviewLevels(
                    leftSample,
                    rightSample,
                    caseSample,
                    leftCharging,
                    rightCharging,
                    caseCharging,
                  )
                },
                label = { Text("⚡ К") },
              )
            }
          }
        }
      }
    }
  }
}

package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.MainUiState

@Composable
fun SettingsScreen(
  uiState: MainUiState,
  onToggleAutoShow: (Boolean) -> Unit,
  onToggleAutoHide: (Boolean) -> Unit,
  onToggleShowCaseInCompact: (Boolean) -> Unit,
  onToggleLowBatteryAlert: (Boolean) -> Unit,
  onLowBatteryThresholdChange: (Int) -> Unit,
  onToggleDarkTheme: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val settings = uiState.settings

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text(
        text = "Настройки",
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = "Поведение виджета в шторке уведомлений и автозапуск",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Автоматизация шторки", style = MaterialTheme.typography.titleMedium)
          }

          SettingSwitchRow(
            title = "Включать при подключении",
            subtitle = "Автоматически запускать виджет в шторке при подключении наушников",
            checked = settings.autoShowOnConnect,
            onCheckedChange = onToggleAutoShow,
            tag = "setting_auto_show_switch",
          )

          SettingSwitchRow(
            title = "Скрывать при отключении",
            subtitle = "Убирать уведомление сразу после отключения TWS гарнитуры",
            checked = settings.autoHideOnDisconnect,
            onCheckedChange = onToggleAutoHide,
            tag = "setting_auto_hide_switch",
          )

          SettingSwitchRow(
            title = "Показывать кейс (К) в компактном виде",
            subtitle = "Формат строки: Л: 100%  П: 100%  К: 100%",
            checked = settings.showCaseInCompact,
            onCheckedChange = onToggleShowCaseInCompact,
            tag = "setting_show_case_switch",
          )
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.BatteryAlert,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Уведомление о разряде", style = MaterialTheme.typography.titleMedium)
          }

          SettingSwitchRow(
            title = "Предупреждать о низком заряде",
            subtitle = "Отправлять сигнал, когда заряд любого наушника падает ниже порога",
            checked = settings.lowBatteryAlertEnabled,
            onCheckedChange = onToggleLowBatteryAlert,
            tag = "setting_low_battery_switch",
          )

          if (settings.lowBatteryAlertEnabled) {
            Column {
              Text(
                text = "Порог предупреждения: ${settings.lowBatteryThreshold}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Slider(
                value = settings.lowBatteryThreshold.toFloat(),
                onValueChange = { onLowBatteryThresholdChange(it.toInt()) },
                valueRange = 5f..40f,
              )
            }
          }
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.DarkMode,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
          }
          SettingSwitchRow(
            title = "Тёмная тема",
            subtitle = "Глубокая контрастная палитра для удобного чтения уровня заряда",
            checked = settings.useDarkTheme,
            onCheckedChange = onToggleDarkTheme,
            tag = "setting_dark_theme_switch",
          )
        }
      }
    }

    // MIUI / HyperOS Background Autostart & Battery Optimization Helper (Redmi Note 10 Pro)
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.BatterySaver,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Настройка для Xiaomi / MIUI", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "На смартфонах Redmi / Xiaomi система MIUI может закрывать фоновый приёмник Bluetooth. Для стабильного автозапуска виджета с Redmi Buds 6 Pro включите «Автозапуск» и выберите «Нет ограничений» в контроле активности.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          FilledTonalButton(
            onClick = {
              val miuiIntent =
                Intent().apply {
                  component =
                    android.content.ComponentName(
                      "com.miui.securitycenter",
                      "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )
                }
              val fallbackIntent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
              try {
                context.startActivity(miuiIntent)
              } catch (_: Exception) {
                context.startActivity(fallbackIntent)
              }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_miui_autostart_button"),
          ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Автозапуск MIUI")
          }
          Spacer(modifier = Modifier.height(8.dp))
          FilledTonalButton(
            onClick = {
              val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
              context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_app_settings_button"),
          ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Контроль активности (Настройки)")
          }
        }
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(tag),
    )
  }
}

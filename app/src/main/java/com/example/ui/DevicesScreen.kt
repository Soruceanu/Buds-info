package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.MainUiState
import com.example.model.EarbudBrandPreset
import com.example.model.PairedAudioDevice
import com.example.ui.theme.MintElectric

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
  uiState: MainUiState,
  onSelectDevice: (PairedAudioDevice) -> Unit,
  onSelectPreset: (EarbudBrandPreset) -> Unit,
  onRefresh: () -> Unit,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Мои наушники",
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text = "Сопряжённые устройства Bluetooth и выбор протокола",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onRefresh,
          modifier = Modifier.size(48.dp).testTag("refresh_devices_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Обновить список устройств",
          )
        }
      }
    }

    if (!uiState.permissions.hasBluetoothConnect) {
      item {
        PermissionStatusBanner(
          missingConnect = true,
          missingNotifications = !uiState.permissions.hasNotifications,
          onGrantClick = onRequestPermissions,
        )
      }
    }

    // Protocol Preset Selector
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Протокол декодирования TWS",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = uiState.settings.preferredBrandPreset.subtitleRu,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(10.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            EarbudBrandPreset.entries.forEach { preset ->
              FilterChip(
                selected = uiState.settings.preferredBrandPreset == preset,
                onClick = { onSelectPreset(preset) },
                label = { Text(preset.labelRu) },
              )
            }
          }
        }
      }
    }

    if (uiState.pairedDevices.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Rounded.Headphones,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Сопряжённые наушники не найдены",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Подключите ваши Xiaomi Buds, Redmi AirDots или любые TWS наушники в настройках Bluetooth телефона.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
              },
              modifier = Modifier.height(48.dp).testTag("open_bluetooth_settings_button"),
            ) {
              Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Открыть Bluetooth")
            }
          }
        }
      }
    } else {
      items(uiState.pairedDevices, key = { it.address }) { device ->
        val isPreferred = uiState.settings.preferredDeviceAddress == device.address
        Card(
          shape = RoundedCornerShape(20.dp),
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (device.isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface,
            ),
          border =
            BorderStroke(
              1.dp,
              if (isPreferred || device.isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
              else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onSelectDevice(device) }
              .testTag("paired_device_card_${device.address}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (device.isConnected) MintElectric.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.size(46.dp),
            ) {
              Icon(
                imageVector =
                  if (device.isConnected) Icons.Rounded.BluetoothConnected
                  else Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint =
                  if (device.isConnected) MintElectric
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(11.dp),
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${device.detectedBrand.labelRu} • ${device.address}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (device.classicBatteryPercent != null) {
                Text(
                  text = "Заряд системы: ${device.classicBatteryPercent}%",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Medium,
                )
              }
            }

            if (isPreferred || device.isConnected) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Активное устройство",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

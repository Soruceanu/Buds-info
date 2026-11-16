package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DevicesScreen
import com.example.ui.MonitorScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.BudsPulseTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      val permissionLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) {
          viewModel.onPermissionsUpdated()
        }

      BudsPulseTheme(darkTheme = uiState.settings.useDarkTheme) {
        BudsPulseApp(
          uiState = uiState,
          onSelectTab = viewModel::selectTab,
          onRequestPermissions = {
            permissionLauncher.launch(viewModel.requiredRuntimePermissions())
          },
          onRefreshTelemetry = viewModel::refreshAll,
          onToggleShadeWidget = viewModel::toggleShadeWidget,
          onUpdatePreviewLevels = viewModel::updatePreviewLevels,
          onSelectDevice = viewModel::selectPreferredDevice,
          onSelectPreset = viewModel::setBrandPreset,
          onToggleAutoShow = viewModel::setAutoShowOnConnect,
          onToggleAutoHide = viewModel::setAutoHideOnDisconnect,
          onToggleShowCaseInCompact = viewModel::setShowCaseInCompact,
          onToggleLowBatteryAlert = viewModel::setLowBatteryAlertEnabled,
          onLowBatteryThresholdChange = viewModel::setLowBatteryThreshold,
          onToggleDarkTheme = viewModel::setDarkTheme,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudsPulseApp(
  uiState: MainUiState,
  onSelectTab: (AppTab) -> Unit,
  onRequestPermissions: () -> Unit,
  onRefreshTelemetry: () -> Unit,
  onToggleShadeWidget: () -> Unit,
  onUpdatePreviewLevels: (Int, Int, Int, Boolean, Boolean, Boolean) -> Unit,
  onSelectDevice: (com.example.model.PairedAudioDevice) -> Unit,
  onSelectPreset: (com.example.model.EarbudBrandPreset) -> Unit,
  onToggleAutoShow: (Boolean) -> Unit,
  onToggleAutoHide: (Boolean) -> Unit,
  onToggleShowCaseInCompact: (Boolean) -> Unit,
  onToggleLowBatteryAlert: (Boolean) -> Unit,
  onLowBatteryThresholdChange: (Int) -> Unit,
  onToggleDarkTheme: (Boolean) -> Unit,
) {
  BackHandler(enabled = uiState.selectedTab != AppTab.MONITOR) {
    onSelectTab(AppTab.MONITOR)
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "BudsPulse",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
            ),
        )
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            AppTab.entries.forEach { tab ->
              val selected = uiState.selectedTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabIcon(tab = tab, selected = selected) },
                label = { Text(tab.titleRu) },
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail {
            AppTab.entries.forEach { tab ->
              val selected = uiState.selectedTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabIcon(tab = tab, selected = selected) },
                label = { Text(tab.titleRu) },
                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}"),
              )
            }
          }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          val contentModifier = Modifier.widthIn(max = 680.dp)
          when (uiState.selectedTab) {
            AppTab.MONITOR ->
              MonitorScreen(
                uiState = uiState,
                onRequestPermissions = onRequestPermissions,
                onRefreshTelemetry = onRefreshTelemetry,
                onToggleShadeWidget = onToggleShadeWidget,
                onToggleAutoShow = onToggleAutoShow,
                onUpdatePreviewLevels = onUpdatePreviewLevels,
                modifier = contentModifier,
              )
            AppTab.DEVICES ->
              DevicesScreen(
                uiState = uiState,
                onSelectDevice = onSelectDevice,
                onSelectPreset = onSelectPreset,
                onRefresh = onRefreshTelemetry,
                onRequestPermissions = onRequestPermissions,
                modifier = contentModifier,
              )
            AppTab.SETTINGS ->
              SettingsScreen(
                uiState = uiState,
                onToggleAutoShow = onToggleAutoShow,
                onToggleAutoHide = onToggleAutoHide,
                onToggleShowCaseInCompact = onToggleShowCaseInCompact,
                onToggleLowBatteryAlert = onToggleLowBatteryAlert,
                onLowBatteryThresholdChange = onLowBatteryThresholdChange,
                onToggleDarkTheme = onToggleDarkTheme,
                modifier = contentModifier,
              )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIcon(tab: AppTab, selected: Boolean) {
  val icon =
    when (tab) {
      AppTab.MONITOR -> if (selected) Icons.Rounded.Tune else Icons.Outlined.Tune
      AppTab.DEVICES -> if (selected) Icons.Rounded.Headphones else Icons.Outlined.Headphones
      AppTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
    }
  Icon(imageVector = icon, contentDescription = tab.titleRu)
}

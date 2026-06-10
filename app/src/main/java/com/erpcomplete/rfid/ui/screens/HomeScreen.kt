package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.rfid.RfidConnectionState
import com.erpcomplete.rfid.ui.components.BluetoothDisabledBanner
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ReaderStatusCard
import com.erpcomplete.rfid.ui.components.ErpGradientHeader
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.HomeAnalyticsSection
import com.erpcomplete.rfid.ui.components.SyncStatusChip
import com.erpcomplete.rfid.ui.components.WorkspaceContextCard
import com.erpcomplete.rfid.ui.components.rememberHomeAnalytics
import com.erpcomplete.rfid.ui.navigation.Routes
import com.erpcomplete.rfid.ui.navigation.navigateBottomTab
import com.erpcomplete.rfid.ui.navigation.navigateWorkflow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    navController: NavHostController,
    rootNavController: NavHostController,
) {
    val userName by container.authStore.userName.collectAsState(initial = null)
    val warehouseName by container.authStore.warehouseName.collectAsState(initial = null)
    val buName by container.authStore.businessUnitName.collectAsState(initial = null)
    val teamName by container.authStore.teamName.collectAsState(initial = null)
    val businessUnitId by container.authStore.businessUnitId.collectAsState(initial = null)
    val teamId by container.authStore.teamId.collectAsState(initial = null)
    val warehouseId by container.authStore.warehouseId.collectAsState(initial = null)
    val workspaceKey = "$businessUnitId|$teamId|$warehouseId"
    val pending by container.syncRepository.pendingCount.collectAsState()
    val isConnected by container.rfidManager.isDeviceConnected.collectAsState()
    val readerName by container.rfidManager.connectionState.collectAsState()
    val diagnostics by container.rfidManager.diagnostics.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmWorkspaceChange by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (isConnected) container.rfidManager.refreshDiagnostics()
    }
    val analytics = rememberHomeAnalytics(
        container = container,
        readerOnline = isConnected,
        syncPending = pending,
        workspaceKey = workspaceKey,
    )

    fun openWorkspacePicker() {
        confirmWorkspaceChange = true
    }

    if (confirmWorkspaceChange) {
        AlertDialog(
            onDismissRequest = { confirmWorkspaceChange = false },
            title = { Text("Change workspace?") },
            text = { Text("Unsaved work on open screens may be lost. Continue to pick another warehouse?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWorkspaceChange = false
                        scope.launch {
                            container.authStore.clearWorkspace()
                            rootNavController.navigate(Routes.WORKSPACE) {
                                popUpTo(Routes.MAIN) { inclusive = true }
                            }
                        }
                    },
                ) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWorkspaceChange = false }) { Text("Cancel") }
            },
        )
    }

    ErpScaffold(
        title = "Dashboard",
        subtitle = warehouseName ?: buName,
        actions = {
            IconButton(onClick = analytics.refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh analytics")
            }
            SyncStatusChip(pending)
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BluetoothDisabledBanner(compact = true)
            ErpGradientHeader(
                title = "Hello, ${userName?.substringBefore(' ') ?: "Operator"}",
                subtitle = "Warehouse RFID — live queue & device status",
            )

            WorkspaceContextCard(
                warehouseName = warehouseName,
                businessUnitName = buName,
                teamName = teamName,
                onChangeClick = { openWorkspacePicker() },
            )

            HomeAnalyticsSection(
                state = analytics,
                onPutawayClick = { navController.navigateWorkflow(Routes.PUTAWAY) },
                onPickClick = { navController.navigateWorkflow(Routes.PICK) },
                onOpnameClick = { navController.navigateWorkflow(Routes.CYCLE_COUNT) },
                onReceiveClick = { navController.navigateWorkflow(Routes.RECEIVE) },
            )

            if (isConnected && diagnostics.connected) {
                ReaderStatusCard(diagnostics = diagnostics, showSerialHelp = false)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ErpCard(Modifier.weight(1f)) {
                        Text("Reader", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (isConnected) "Online" else "Offline",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ErpCard(Modifier.weight(1f)) {
                        Text("Device", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (val s = readerName) {
                                is RfidConnectionState.Connected -> s.readerName
                                is RfidConnectionState.Pairing -> "Pairing…"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                    }
                }
            }

            ErpPrimaryButton(
                text = "Connect RFD90",
                onClick = { navController.navigateBottomTab(Routes.CONNECT) },
            )

            ErpCard(onClick = { navController.navigateBottomTab(Routes.OPERATIONS) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Warehouse workflows", style = MaterialTheme.typography.titleMedium)
                        Text("Receive, putaway, pick, count, encode", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ErpCard {
                Text("RFID read sync", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (pending > 0) {
                        "$pending read batch${if (pending == 1) "" else "es"} queued — uploading when online"
                    } else {
                        "Scans upload automatically; no manual sync needed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

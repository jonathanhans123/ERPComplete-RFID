package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.model.BusinessUnitSummary
import com.erpcomplete.rfid.data.model.WorkspaceOption
import com.erpcomplete.rfid.data.model.WorkspacesPayload
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpGradientHeader
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.WorkflowJson
import kotlinx.coroutines.launch

@Composable
fun WorkspaceSelectionScreen(
    container: AppContainer,
    onSelected: () -> Unit,
    onLogout: () -> Unit,
) {
    var payload by remember { mutableStateOf<WorkspacesPayload?>(null) }
    var selectedBuId by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userName by container.authStore.userName.collectAsState(initial = null)
    val currentWarehouseId by container.authStore.warehouseId.collectAsState(initial = null)
    val currentBusinessUnitId by container.authStore.businessUnitId.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val res = container.api.listWorkspaces()
            if (!res.isSuccessful) {
                error = ApiErrorParser.httpMessage(res)
                payload = null
                return@LaunchedEffect
            }
            val loaded = WorkflowJson.envelopeWorkspacesPayload(res)
            payload = loaded
            if (loaded.businessUnits.isEmpty()) {
                error = "No business unit assigned to this account. Contact your administrator."
            }
        } catch (e: Exception) {
            error = ApiErrorParser.networkMessage(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(payload, currentBusinessUnitId) {
        val data = payload ?: return@LaunchedEffect
        val preferred = currentBusinessUnitId?.toLongOrNull()
            ?.takeIf { id -> data.businessUnits.any { it.id == id } }
            ?: data.businessUnits.firstOrNull()?.id
        if (preferred != null && (selectedBuId == null || data.businessUnits.none { it.id == selectedBuId })) {
            selectedBuId = preferred
        }
    }

    val businessUnits = payload?.businessUnits.orEmpty()
    val warehousesForBu = payload?.warehouses
        ?.filter { it.businessUnitId == selectedBuId }
        .orEmpty()
        .sortedBy { it.warehouseName.lowercase() }

    ErpScaffold(
        title = "Select workspace",
        subtitle = userName?.takeIf { it.isNotBlank() },
        actions = {
            TextButton(onClick = {
                scope.launch {
                    container.authStore.clear()
                    onLogout()
                }
            }) { Text("Logout") }
        },
    ) {
        ErpGradientHeader(
            title = "Where are you working?",
            subtitle = "Choose business unit, then warehouse — team is set automatically",
        )

        error?.let { StatusBanner(it, isError = true) }

        if (loading) {
            Text("Loading workspaces…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (businessUnits.isNotEmpty()) {
                    ErpCard {
                        Text(
                            "Business unit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            businessUnits.forEach { bu ->
                                BusinessUnitOptionCard(
                                    businessUnit = bu,
                                    selected = selectedBuId == bu.id,
                                    isCurrent = currentBusinessUnitId == bu.id.toString(),
                                    onClick = { selectedBuId = bu.id },
                                )
                            }
                        }
                    }
                }

                ErpCard {
                    val selectedBuName = businessUnits.firstOrNull { it.id == selectedBuId }?.name
                    Text(
                        "Warehouse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    selectedBuName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (selectedBuId == null) {
                        Text(
                            "Select a business unit first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (warehousesForBu.isEmpty()) {
                        Text(
                            "No warehouses assigned for this business unit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            warehousesForBu.forEach { workspace ->
                                WarehouseOptionCard(
                                    workspace = workspace,
                                    isCurrent = currentWarehouseId == workspace.warehouseId.toString(),
                                    onClick = {
                                        if (workspace.teamId == null) {
                                            error =
                                                "${workspace.warehouseName} has no team mapped. Contact your administrator."
                                            return@WarehouseOptionCard
                                        }
                                        scope.launch {
                                            try {
                                                error = null
                                                container.authStore.saveWorkspace(workspace)
                                                onSelected()
                                            } catch (e: Exception) {
                                                error = e.message
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessUnitOptionCard(
    businessUnit: BusinessUnitSummary,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Business,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                businessUnit.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when (businessUnit.warehouseCount) {
                    0 -> "No warehouses"
                    1 -> "1 warehouse"
                    else -> "${businessUnit.warehouseCount} warehouses"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isCurrent) {
                Text(
                    "Current unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WarehouseOptionCard(
    workspace: WorkspaceOption,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                workspace.warehouseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            workspace.warehouseCode?.takeIf { it.isNotBlank() }?.let { code ->
                Text(
                    code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            workspace.teamName?.takeIf { it.isNotBlank() }?.let { team ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Team: $team",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isCurrent) {
                Text(
                    "Current warehouse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

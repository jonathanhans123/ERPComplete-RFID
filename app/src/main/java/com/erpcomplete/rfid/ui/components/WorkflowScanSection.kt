package com.erpcomplete.rfid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.rfid.RfidManager

@Composable
fun WorkflowScanSection(
    container: AppContainer,
    tags: List<RfidManager.ScannedTag>,
    onClear: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    var registerCode by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val resolveMap = rememberScanResolver(container.api, tags.map { it.epc }, refreshKey)

    WorkflowScanPanel(
        tags = tags,
        resolveMap = resolveMap,
        onClear = onClear,
        onUnknownClick = { registerCode = it },
        modifier = modifier,
    )
    TagRegistrationSheet(
        visible = registerCode != null,
        code = registerCode,
        container = container,
        onDismiss = { registerCode = null },
        onRegistered = { refreshKey++ },
    )
}

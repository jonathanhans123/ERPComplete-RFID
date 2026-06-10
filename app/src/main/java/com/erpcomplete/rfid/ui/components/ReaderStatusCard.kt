package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.rfid.ReaderDiagnostics

@Composable
fun ReaderStatusCard(
    diagnostics: ReaderDiagnostics,
    modifier: Modifier = Modifier,
    showSerialHelp: Boolean = true,
) {
    ErpCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                diagnostics.readerName ?: "RFD90",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Scanner battery",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ScannerBatteryMeter(
                percent = diagnostics.batteryPercent,
                charging = diagnostics.batteryCharging,
                loading = diagnostics.batteryLoading,
            )
            ReaderSerialBlock(
                serialNumber = diagnostics.serialNumber,
                readerName = diagnostics.readerName,
                showHelp = showSerialHelp,
            )
        }
    }
}

@Composable
fun ReaderSerialBlock(
    serialNumber: String?,
    readerName: String? = null,
    showHelp: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Reader serial (S/N)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            serialNumber ?: "Not reported by reader yet",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        if (serialNumber == null && !readerName.isNullOrBlank()) {
            Text(
                "Bluetooth name: $readerName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showHelp) {
            Spacer(Modifier.height(6.dp))
            Text(
                "On the back label: S/N is for identification and support. Configure sled Wi‑Fi with 123RFID Desktop if needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.rfid.FirmwareUpdateState

@Composable
fun FirmwareUpdateMeter(
    state: FirmwareUpdateState,
    modifier: Modifier = Modifier,
) {
    val active = state.phase == FirmwareUpdateState.Phase.DOWNLOADING ||
        state.phase == FirmwareUpdateState.Phase.INSTALLING
    val fraction = (state.progressPercent.coerceIn(0, 100)) / 100f
    val meterColor = when (state.phase) {
        FirmwareUpdateState.Phase.SUCCESS -> Color(0xFF2E7D32)
        FirmwareUpdateState.Phase.FAILED -> Color(0xFFD32F2F)
        FirmwareUpdateState.Phase.UPDATE_AVAILABLE,
        FirmwareUpdateState.Phase.READY_TO_INSTALL,
        -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "0%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (active) "${state.progressPercent}%" else state.phase.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = meterColor,
            )
            Text(
                "100%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(trackColor),
        ) {
            if (active && fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(meterColor),
                )
            }
        }
        state.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScannerBatteryMeter(
    percent: Int?,
    charging: Boolean? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val level = percent?.coerceIn(0, 100)
    val fraction = (level ?: 0) / 100f
    val hasReading = level != null
    val isPending = loading && !hasReading
    val meterColor = when {
        !hasReading -> MaterialTheme.colorScheme.onSurfaceVariant
        charging == true -> MaterialTheme.colorScheme.primary
        level >= 50 -> Color(0xFF2E7D32)
        level >= 20 -> Color(0xFFF9A825)
        else -> Color(0xFFD32F2F)
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Low",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD32F2F),
            )
            Text(
                when {
                    isPending -> "…"
                    !hasReading -> "—"
                    charging == true -> "$level% · charging"
                    else -> "$level%"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = meterColor,
            )
            Text(
                "Full",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E7D32),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(trackColor),
        ) {
            if (isPending) {
                Box(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
            } else if (hasReading) {
                val fillBrush = if (charging == true) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.primary,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFD32F2F),
                            Color(0xFFF9A825),
                            Color(0xFF2E7D32),
                        ),
                    )
                }
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(fillBrush),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                isPending -> "Reading battery from reader…"
                !hasReading -> "Battery level unavailable — tap refresh in Settings"
                charging == true -> "Charging"
                level >= 50 -> "Battery good"
                level >= 20 -> "Battery moderate — charge when convenient"
                else -> "Battery low — charge soon"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

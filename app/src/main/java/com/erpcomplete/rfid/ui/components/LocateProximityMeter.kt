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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R

@Composable
fun LocateProximityMeter(
    strength: Int?,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = (strength ?: 0).coerceIn(0, 100)
    val fraction = level / 100f
    val meterColor = lerp(
        Color(0xFFD32F2F),
        Color(0xFF2E7D32),
        fraction,
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.locate_label_far),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD32F2F),
            )
            Text(
                if (isActive) "$level%" else stringResource(R.string.symbol_em_dash),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) meterColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.locate_label_close),
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
            if (isActive && fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFD32F2F),
                                    Color(0xFFF9A825),
                                    Color(0xFF2E7D32),
                                ),
                            ),
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !isActive -> stringResource(R.string.locate_hold_trigger_search)
                level >= 75 -> stringResource(R.string.locate_very_close)
                level >= 45 -> stringResource(R.string.locate_getting_warmer)
                level >= 15 -> stringResource(R.string.locate_weak_signal)
                else -> stringResource(R.string.locate_no_signal)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

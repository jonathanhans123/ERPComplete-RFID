package com.erpcomplete.rfid.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f),
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    widthFraction: Float = 1f,
) {
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(shimmerBrush()),
    )
}

@Composable
fun WorkflowListCardSkeleton(count: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            ErpCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(height = 20.dp, widthFraction = 0.55f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SkeletonBox(Modifier.weight(0.35f), height = 14.dp)
                        SkeletonBox(Modifier.weight(0.25f), height = 14.dp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SkeletonBox(Modifier.weight(0.3f), height = 14.dp)
                        SkeletonBox(Modifier.weight(0.2f), height = 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowFormSkeleton(
    fieldCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(fieldCount) {
            SkeletonBox(height = 52.dp)
        }
        Spacer(Modifier.height(4.dp))
        SkeletonBox(height = 46.dp, widthFraction = 1f)
    }
}

@Composable
fun WorkflowTableSkeleton(
    rows: Int = 5,
    columns: Int = 4,
    modifier: Modifier = Modifier,
) {
    ErpCard(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) {
                    SkeletonBox(Modifier.weight(1f), height = 12.dp)
                }
            }
            repeat(rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(columns) {
                        SkeletonBox(Modifier.weight(1f), height = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMetricsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(height = 18.dp, widthFraction = 0.4f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                ErpCard(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(height = 12.dp, widthFraction = 0.5f)
                        SkeletonBox(height = 28.dp, widthFraction = 0.35f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                ErpCard(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(height = 12.dp, widthFraction = 0.5f)
                        SkeletonBox(height = 28.dp, widthFraction = 0.35f)
                    }
                }
            }
        }
    }
}

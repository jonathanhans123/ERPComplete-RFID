package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.ui.navigation.Routes

private const val BAR_HEIGHT = 56
private const val FAB_SIZE = 52
private const val FAB_LIFT = (FAB_SIZE - 24) / 2

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val isCenter: Boolean = false,
)

@Composable
fun ErpBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavItem(Routes.HOME, R.string.nav_home, Icons.Default.Home),
        NavItem(Routes.CONNECT, R.string.nav_connect, Icons.Default.BluetoothConnected),
        NavItem(Routes.OPERATIONS, R.string.nav_operations, Icons.Default.Warehouse, isCenter = true),
        NavItem(Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
        NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
    )

    val centerItem = items.first { it.isCenter }
    val centerSelected = currentRoute == centerItem.route
    val centerColor = if (centerSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.inverseSurface
    }
    val centerIconColor = if (centerSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.inverseOnSurface
    }
    val centerLabel = stringResource(centerItem.labelRes)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((BAR_HEIGHT + FAB_LIFT).dp)
            .graphicsLayer { clip = false }
            .navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BAR_HEIGHT.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    if (item.isCenter) {
                        CenterNavLabelSlot(Modifier.weight(1f))
                    } else {
                        SideNavSlot(item, selected, onNavigate, Modifier.weight(1f))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onNavigate(centerItem.route) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(FAB_SIZE.dp),
            shape = CircleShape,
            containerColor = centerColor,
            contentColor = centerIconColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp,
            ),
        ) {
            Icon(centerItem.icon, contentDescription = centerLabel, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SideNavSlot(
    item: NavItem,
    selected: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(item.labelRes)
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(item.route) },
            )
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CenterNavLabelSlot(modifier: Modifier = Modifier) {
    // Reserve space under the center FAB; icon-only (screen reader: FAB contentDescription).
    Box(
        modifier = modifier
            .padding(bottom = 4.dp)
            .height(28.dp),
    )
}

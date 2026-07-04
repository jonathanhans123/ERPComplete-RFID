package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.WorkflowTile
import com.erpcomplete.rfid.ui.navigation.Routes

private data class Op(val route: String, val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun OperationsScreen(navController: NavHostController) {
    val ops = listOf(
        Op(Routes.RECEIVE, stringResource(R.string.op_goods_receipt_title), stringResource(R.string.op_goods_receipt_desc), Icons.Default.LocalShipping),
        Op(Routes.PUTAWAY, stringResource(R.string.op_putaway_title), stringResource(R.string.op_putaway_desc), Icons.Default.AddLocation),
        Op(Routes.PICK, stringResource(R.string.op_pick_title), stringResource(R.string.op_pick_desc), Icons.Default.ShoppingCart),
        Op(Routes.CYCLE_COUNT, stringResource(R.string.op_cycle_count_title), stringResource(R.string.op_cycle_count_desc), Icons.Default.Inventory),
        Op(Routes.RFID_SESSION, stringResource(R.string.op_rfid_session_title), stringResource(R.string.op_rfid_session_desc), Icons.Default.Radar),
        Op(Routes.ENCODE, stringResource(R.string.encode_title), stringResource(R.string.op_encode_desc), Icons.Default.QrCode),
        Op(Routes.LOCATE, stringResource(R.string.locate_title), stringResource(R.string.op_locate_desc), Icons.Default.Search),
        Op(Routes.INVENTORY, stringResource(R.string.inventory_title), stringResource(R.string.op_inventory_desc), Icons.Default.Store),
    )

    ErpScaffold(
        title = stringResource(R.string.operations_title),
        subtitle = stringResource(R.string.operations_subtitle),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ops) { op ->
                WorkflowTile(
                    title = op.title,
                    description = op.desc,
                    icon = op.icon,
                    onClick = { navController.navigate(op.route) },
                )
            }
        }
    }
}

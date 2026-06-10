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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.WorkflowTile
import com.erpcomplete.rfid.ui.navigation.Routes

private data class Op(val route: String, val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun OperationsScreen(navController: NavHostController) {
    val ops = listOf(
        Op(Routes.RECEIVE, "Goods receipt", "Match tags to PO lines", Icons.Default.LocalShipping),
        Op(Routes.PUTAWAY, "Putaway", "Assign tags to locations", Icons.Default.AddLocation),
        Op(Routes.PICK, "Pick list", "Pick, pack & cut", Icons.Default.ShoppingCart),
        Op(Routes.CYCLE_COUNT, "Stock opname", "Count & approve ongoing", Icons.Default.Inventory),
        Op(Routes.RFID_SESSION, "RFID location count", "Scan all tags at a bin", Icons.Default.Radar),
        Op(Routes.ENCODE, "Encode tag", "Write new EPC to label", Icons.Default.QrCode),
        Op(Routes.LOCATE, "Locate item", "Find tag in warehouse", Icons.Default.Search),
        Op(Routes.INVENTORY, "Inventory", "Warehouses, locations & stock", Icons.Default.Store),
    )

    ErpScaffold(title = "Work", subtitle = "Warehouse RFID workflows") {
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

package com.erpcomplete.rfid.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.util.parseInventoryDeepLink
import com.erpcomplete.rfid.rfid.scanSessionForRoute
import com.erpcomplete.rfid.ui.components.ErpBottomBar
import com.erpcomplete.rfid.ui.components.BluetoothDisabledBanner
import com.erpcomplete.rfid.ui.screens.ApprovalsScreen
import com.erpcomplete.rfid.ui.screens.ConnectScreen
import com.erpcomplete.rfid.ui.screens.CycleCountScreen
import com.erpcomplete.rfid.ui.screens.StockOpnameScreen
import com.erpcomplete.rfid.ui.screens.EncodeScreen
import com.erpcomplete.rfid.ui.screens.HomeScreen
import com.erpcomplete.rfid.ui.screens.InventoryScreen
import com.erpcomplete.rfid.ui.screens.LocateScreen
import com.erpcomplete.rfid.ui.screens.OperationsScreen
import com.erpcomplete.rfid.ui.screens.PickScreen
import com.erpcomplete.rfid.ui.screens.PutawayScreen
import com.erpcomplete.rfid.ui.screens.ReceiveScreen
import com.erpcomplete.rfid.ui.screens.SearchScreen
import com.erpcomplete.rfid.ui.screens.SettingsScreen

@Composable
fun MainShell(
    container: AppContainer,
    rootNavController: NavHostController,
    openRoute: String? = null,
) {
    val innerNav = rememberNavController()
    val backStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val selectedTab = TabNavigation.tabItemForDestination(currentRoute)

    LaunchedEffect(openRoute) {
        when (openRoute) {
            com.erpcomplete.rfid.notify.WarehouseNotificationHelper.ROUTE_RECEIVE ->
                innerNav.navigate(Routes.RECEIVE) { launchSingleTop = true }
            com.erpcomplete.rfid.notify.WarehouseNotificationHelper.ROUTE_PUTAWAY ->
                innerNav.navigate(Routes.PUTAWAY) { launchSingleTop = true }
            com.erpcomplete.rfid.notify.WarehouseNotificationHelper.ROUTE_PICK ->
                innerNav.navigate(Routes.PICK) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        container.rfidManager.setScanSession(scanSessionForRoute(currentRoute))
    }

    Scaffold(
        bottomBar = {
            Box(Modifier.graphicsLayer { clip = false }) {
                ErpBottomBar(
                    currentRoute = selectedTab,
                    onNavigate = { innerNav.navigateBottomTab(it) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            BluetoothDisabledBanner(Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            NavHost(
                navController = innerNav,
                startDestination = Routes.TAB_HOME,
                modifier = Modifier.weight(1f),
            ) {
                navigation(route = Routes.TAB_HOME, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(container, innerNav, rootNavController) }
                }
                navigation(route = Routes.TAB_CONNECT, startDestination = Routes.CONNECT) {
                    composable(Routes.CONNECT) { ConnectScreen(container) }
                }
                navigation(route = Routes.TAB_OPERATIONS, startDestination = Routes.OPERATIONS) {
                    composable(Routes.OPERATIONS) { OperationsScreen(innerNav) }
                    composable(Routes.RECEIVE) {
                        ReceiveScreen(
                            container = container,
                            onBack = { innerNav.popBackStack() },
                            onOpenPutaway = { taskId ->
                                innerNav.navigate("${Routes.PUTAWAY}?taskId=$taskId")
                            },
                        )
                    }
                    composable(
                        route = "${Routes.PUTAWAY}?taskId={taskId}",
                        arguments = listOf(
                            navArgument("taskId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val taskId = entry.arguments?.getString("taskId")?.toLongOrNull()
                        PutawayScreen(
                            container = container,
                            initialTaskId = taskId,
                            onBack = { innerNav.popBackStack() },
                        )
                    }
                    composable(Routes.PUTAWAY) {
                        PutawayScreen(container = container, onBack = { innerNav.popBackStack() })
                    }
                    composable(Routes.PICK) { PickScreen(container) { innerNav.popBackStack() } }
                    composable(Routes.CYCLE_COUNT) { StockOpnameScreen(container) { innerNav.popBackStack() } }
                    composable(Routes.RFID_SESSION) { CycleCountScreen(container) { innerNav.popBackStack() } }
                    composable(Routes.ENCODE) { EncodeScreen(container) { innerNav.popBackStack() } }
                    composable(Routes.LOCATE) { LocateScreen(container) { innerNav.popBackStack() } }
                    composable(Routes.APPROVALS) { ApprovalsScreen(container) { innerNav.popBackStack() } }
                    composable(
                        route = "${Routes.INVENTORY}?flow={flow}&productId={productId}&variationId={variationId}&locationId={locationId}&currentQty={currentQty}&isRoll={isRoll}&productName={productName}&variationName={variationName}&batchNumber={batchNumber}&rollNumber={rollNumber}&unit={unit}",
                        arguments = listOf(
                            navArgument("flow") { type = NavType.StringType; defaultValue = "" },
                            navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("variationId") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("locationId") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("currentQty") { type = NavType.StringType; defaultValue = "" },
                            navArgument("isRoll") { type = NavType.StringType; defaultValue = "" },
                            navArgument("productName") { type = NavType.StringType; defaultValue = "" },
                            navArgument("variationName") { type = NavType.StringType; defaultValue = "" },
                            navArgument("batchNumber") { type = NavType.StringType; defaultValue = "" },
                            navArgument("rollNumber") { type = NavType.StringType; defaultValue = "" },
                            navArgument("unit") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        val args = entry.arguments
                        val flow = args?.getString("flow")
                        val preset = parseInventoryDeepLink(
                            flow = flow,
                            productId = args?.getLong("productId") ?: -1L,
                            variationId = args?.getLong("variationId") ?: -1L,
                            locationId = args?.getLong("locationId") ?: 0L,
                            currentQty = args?.getString("currentQty")?.toDoubleOrNull(),
                            isRoll = args?.getString("isRoll") == "1",
                            productName = args?.getString("productName"),
                            variationName = args?.getString("variationName"),
                            batchNumber = args?.getString("batchNumber"),
                            rollNumber = args?.getString("rollNumber"),
                            unit = args?.getString("unit"),
                        )
                        InventoryScreen(
                            container = container,
                            onBack = { innerNav.popBackStack() },
                            initialPreset = preset,
                            initialFlow = flow,
                        )
                    }
                    composable(Routes.INVENTORY) {
                        InventoryScreen(
                            container = container,
                            onBack = { innerNav.popBackStack() },
                        )
                    }
                }
                navigation(route = Routes.TAB_SEARCH, startDestination = Routes.SEARCH) {
                    composable(Routes.SEARCH) { SearchScreen(container, innerNav) }
                }
                navigation(route = Routes.TAB_SETTINGS, startDestination = Routes.SETTINGS) {
                    composable(Routes.SETTINGS) { SettingsScreen(container, rootNavController) }
                }
            }
        }
    }
}

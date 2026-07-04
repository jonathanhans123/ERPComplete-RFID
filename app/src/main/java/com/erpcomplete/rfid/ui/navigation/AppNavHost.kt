package com.erpcomplete.rfid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.ui.screens.LoginScreen
import com.erpcomplete.rfid.ui.screens.WorkspaceSelectionScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String,
    openRoute: String? = null,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onLoginSuccess = { needsWorkspace ->
                    val dest = if (needsWorkspace) Routes.WORKSPACE else Routes.MAIN
                    navController.navigate(dest) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.WORKSPACE) {
            WorkspaceSelectionScreen(
                container = container,
                onSelected = {
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.WORKSPACE) { inclusive = true } }
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.MAIN) {
            MainShell(container = container, rootNavController = navController, openRoute = openRoute)
        }
    }
}

package com.erpcomplete.rfid.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

object TabNavigation {
    fun tabGraphFor(itemRoute: String): String = when (itemRoute) {
        Routes.CONNECT -> Routes.TAB_CONNECT
        Routes.OPERATIONS -> Routes.TAB_OPERATIONS
        Routes.SEARCH -> Routes.TAB_SEARCH
        Routes.SETTINGS -> Routes.TAB_SETTINGS
        else -> Routes.TAB_HOME
    }

    /** Bottom-bar tab to highlight for any destination route (including workflow screens). */
    fun tabItemForDestination(route: String?): String {
        if (route == null) return Routes.HOME
        val base = route.substringBefore('?')
        return when {
            base == Routes.CONNECT -> Routes.CONNECT
            base == Routes.SEARCH -> Routes.SEARCH
            base == Routes.SETTINGS -> Routes.SETTINGS
            base == Routes.HOME -> Routes.HOME
            base == Routes.OPERATIONS || base in Routes.operationsWorkflowRoutes -> Routes.OPERATIONS
            else -> Routes.HOME
        }
    }
}

fun NavHostController.navigateBottomTab(itemRoute: String) {
    val tabGraph = TabNavigation.tabGraphFor(itemRoute)
    navigate(tabGraph) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Open a workflow screen and keep it on the operations tab back stack. */
fun NavHostController.navigateWorkflow(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

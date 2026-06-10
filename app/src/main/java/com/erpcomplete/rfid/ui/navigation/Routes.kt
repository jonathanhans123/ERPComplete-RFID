package com.erpcomplete.rfid.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val WORKSPACE = "workspace"
    const val MAIN = "main"

    const val HOME = "home"
    const val CONNECT = "connect"
    const val OPERATIONS = "operations"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val RECEIVE = "receive"
    const val PUTAWAY = "putaway"
    const val PICK = "pick"
    const val CYCLE_COUNT = "cycle_count"
    const val RFID_SESSION = "rfid_session"
    const val ENCODE = "encode"
    const val LOCATE = "locate"
    const val INVENTORY = "inventory"

    const val TAB_HOME = "tab_home"
    const val TAB_CONNECT = "tab_connect"
    const val TAB_OPERATIONS = "tab_operations"
    const val TAB_SEARCH = "tab_search"
    const val TAB_SETTINGS = "tab_settings"

    val bottomNavItems = listOf(HOME, CONNECT, OPERATIONS, SEARCH, SETTINGS)

    val operationsWorkflowRoutes = setOf(
        RECEIVE,
        PUTAWAY,
        PICK,
        CYCLE_COUNT,
        RFID_SESSION,
        ENCODE,
        LOCATE,
        INVENTORY,
    )

    fun isBottomNavRoute(route: String?): Boolean =
        route != null && bottomNavItems.any { route == it || route.startsWith("$it/") }
}

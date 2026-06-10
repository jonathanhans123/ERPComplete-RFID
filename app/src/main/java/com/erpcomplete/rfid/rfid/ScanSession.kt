package com.erpcomplete.rfid.rfid

import com.erpcomplete.rfid.ui.navigation.Routes

enum class ScanSession {
    NONE,
    SEARCH,
    WORK,
    LOCATE,
    ENCODE,
}

fun scanSessionForRoute(route: String?): ScanSession = when (route) {
    Routes.SEARCH -> ScanSession.SEARCH
    Routes.LOCATE -> ScanSession.LOCATE
    Routes.ENCODE -> ScanSession.ENCODE
    Routes.OPERATIONS,
    Routes.RECEIVE,
    Routes.PUTAWAY,
    Routes.PICK,
    Routes.CYCLE_COUNT,
    -> ScanSession.WORK
    else -> ScanSession.NONE
}

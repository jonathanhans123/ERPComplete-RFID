package com.erpcomplete.rfid.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.model.MobileInventoryPermissions

@Composable
fun rememberMobileInventoryPermissions(container: AppContainer): MobileInventoryPermissions {
    var permissions by remember {
        mutableStateOf(container.authStore.mobileInventoryPermissionsBlocking())
    }
    LaunchedEffect(container) {
        container.refreshMobilePermissions()
        permissions = container.authStore.mobileInventoryPermissionsBlocking()
    }
    return permissions
}

package com.erpcomplete.rfid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erpcomplete.rfid.notify.WarehouseNotificationHelper
import com.erpcomplete.rfid.ui.components.BackgroundReliabilityDialog
import com.erpcomplete.rfid.ui.components.BackgroundReliabilityManualDialog
import com.erpcomplete.rfid.util.BackgroundReliabilityHelper
import com.erpcomplete.rfid.ui.navigation.AppNavHost
import com.erpcomplete.rfid.ui.navigation.Routes
import com.erpcomplete.rfid.ui.theme.ERPCompleteRfidTheme

class MainActivity : ComponentActivity() {

    private var onPermissionsReady: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        onPermissionsReady?.invoke()
        onPermissionsReady = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ErpCompleteRfidApp).container

        val initialOpenRoute = intent?.getStringExtra(WarehouseNotificationHelper.EXTRA_OPEN_ROUTE)
        setContent {
            val session = remember(container) { container.authStore.readSessionSnapshot() }
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            var openRoute by remember { mutableStateOf(initialOpenRoute) }
            var showBgReliability by remember { mutableStateOf(false) }
            var showBgManual by remember { mutableStateOf(false) }
            val context = this@MainActivity

            val loggedIn by container.authStore.isLoggedIn.collectAsState(initial = session.loggedIn)
            val hasWorkspace by container.authStore.hasWorkspaceSelected.collectAsState(initial = session.hasWorkspace)

            val startDestination = remember(session) {
                when {
                    !session.loggedIn -> Routes.LOGIN
                    !session.hasWorkspace -> Routes.WORKSPACE
                    else -> Routes.MAIN
                }
            }

            LaunchedEffect(loggedIn, hasWorkspace) {
                if (loggedIn && hasWorkspace) {
                    val exempt = BackgroundReliabilityHelper.isIgnoringBatteryOptimizations(context)
                    if (!exempt && container.backgroundReliabilityStore.shouldPrompt()) {
                        showBgReliability = true
                    }
                }
            }

            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    container.ensureValidSession()
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            DisposableEffect(lifecycleOwner, loggedIn, hasWorkspace) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && loggedIn) {
                        scope.launch { container.ensureValidSession() }
                        if (hasWorkspace) {
                            val exempt = BackgroundReliabilityHelper.isIgnoringBatteryOptimizations(context)
                            if (exempt) {
                                showBgReliability = false
                                showBgManual = false
                            } else if (container.backgroundReliabilityStore.shouldPromptBlocking()) {
                                showBgReliability = true
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(loggedIn, hasWorkspace, backStackEntry?.destination?.route) {
                val target = when {
                    !loggedIn -> Routes.LOGIN
                    !hasWorkspace -> Routes.WORKSPACE
                    else -> Routes.MAIN
                }
                val current = backStackEntry?.destination?.route
                if (current != null && current != target) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            ERPCompleteRfidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = navController,
                        container = container,
                        startDestination = startDestination,
                        openRoute = openRoute,
                    )
                    LaunchedEffect(openRoute) {
                        if (openRoute != null) openRoute = null
                    }
                }
            }

            if (showBgReliability) {
                BackgroundReliabilityDialog(
                    store = container.backgroundReliabilityStore,
                    onDismiss = { showBgReliability = false },
                    onOpenManualSteps = {
                        showBgReliability = false
                        showBgManual = true
                    },
                )
            }
            if (showBgManual) {
                BackgroundReliabilityManualDialog(onDismiss = { showBgManual = false })
            }
        }

        requestRuntimePermissions {
            container.rfidManager.tryAutoReconnect()
        }
    }

    private fun requestRuntimePermissions(onGranted: () -> Unit) {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isEmpty()) {
            onGranted()
        } else {
            onPermissionsReady = onGranted
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

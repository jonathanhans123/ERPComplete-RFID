package com.erpcomplete.rfid.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.BackgroundReliabilityStore
import com.erpcomplete.rfid.util.BackgroundReliabilityHelper
import kotlinx.coroutines.launch

@Composable
fun BackgroundReliabilityDialog(
    store: BackgroundReliabilityStore,
    onDismiss: () -> Unit,
    onOpenManualSteps: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(text = context.getString(R.string.bg_reliability_title)) },
        text = { Text(text = context.getString(R.string.bg_reliability_message)) },
        confirmButton = {
            TextButton(
                enabled = !busy && activity != null,
                onClick = {
                    if (activity == null) return@TextButton
                    busy = true
                    val launched = BackgroundReliabilityHelper.requestExemption(activity)
                    busy = false
                    if (BackgroundReliabilityHelper.isIgnoringBatteryOptimizations(context)) {
                        onDismiss()
                    } else if (!launched) {
                        onOpenManualSteps()
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator()
                } else {
                    Text(context.getString(R.string.bg_reliability_allow))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        store.snooze()
                        onDismiss()
                    }
                },
            ) {
                Text(context.getString(R.string.bg_reliability_later))
            }
        },
    )
}

@Composable
fun BackgroundReliabilityManualDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = context.getString(R.string.bg_reliability_manual_title)) },
        text = { Text(text = BackgroundReliabilityHelper.manualStepsText(context)) },
        confirmButton = {
            TextButton(onClick = {
                BackgroundReliabilityHelper.openAppDetails(context)
                onDismiss()
            }) {
                Text(context.getString(R.string.bg_reliability_open_app_info))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.bg_reliability_later))
            }
        },
    )
}

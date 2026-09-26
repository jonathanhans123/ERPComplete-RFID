package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.util.DisplayFormat

/**
 * Quantity input for workflow lines: numeric keypad, plus a Fill button on the right that sets
 * the field to [fillValue] — the full amount the line calls for (requested, remaining, system…).
 * Fill is hidden when there is no meaningful full amount or the field is disabled.
 */
@Composable
fun QtyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fillValue: Double?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = fillValue?.takeIf { enabled && it > 0.0 }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = fill?.let { full ->
            {
                TextButton(onClick = { onValueChange(DisplayFormat.qty(full)) }) {
                    Text(stringResource(R.string.action_fill))
                }
            }
        },
        modifier = modifier,
    )
}

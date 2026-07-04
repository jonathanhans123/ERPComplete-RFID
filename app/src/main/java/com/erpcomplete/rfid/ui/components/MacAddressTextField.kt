package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.erpcomplete.rfid.util.PhoneBluetooth

/**
 * Text field tuned for Bluetooth MAC entry from keyboard and hardware wedge scanners.
 */
@Composable
fun MacAddressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onScanComplete: ((String) -> Unit)? = null,
) {
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }

    fun commitParsed(raw: String, notify: Boolean) {
        val parsed = PhoneBluetooth.parseMacInput(raw)
        val next = parsed ?: raw
            .replace("\r", "")
            .replace("\n", "")
            .filter { it.isLetterOrDigit() || it == ':' || it == '-' }
            .uppercase()
        fieldValue = TextFieldValue(next, TextRange(next.length))
        onValueChange(next)
        if (notify && parsed != null) {
            onScanComplete?.invoke(parsed)
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val raw = updated.text
            val hadTerminator = raw.contains('\r') || raw.contains('\n')
            val parsed = PhoneBluetooth.parseMacInput(raw)
            if (parsed != null && (hadTerminator || parsed.length == 12)) {
                commitParsed(raw, notify = true)
            } else {
                fieldValue = updated.copy(text = raw.replace("\r", "").replace("\n", ""))
                onValueChange(fieldValue.text)
            }
        },
        label = label,
        placeholder = placeholder,
        singleLine = true,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> {
                    commitParsed(fieldValue.text, notify = true)
                    true
                }
                else -> false
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { commitParsed(fieldValue.text, notify = true) },
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

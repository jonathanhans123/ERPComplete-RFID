package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R

data class PickerOption(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val searchText: String = listOfNotNull(title, subtitle).joinToString(" "),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchablePickerField(
    label: String,
    selected: PickerOption?,
    placeholder: String,
    enabled: Boolean = true,
    onOpen: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = selected?.title ?: "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        trailingIcon = {
            Row {
                if (selected != null && onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear))
                    }
                }
                IconButton(onClick = onOpen, enabled = enabled) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_choose))
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onOpen() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchablePickerSheet(
    visible: Boolean,
    title: String,
    options: List<PickerOption>,
    loading: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (PickerOption) -> Unit,
    onSearch: ((String) -> Unit)? = null,
    searchHint: String? = null,
) {
    if (!visible) return

    val resolvedSearchHint = searchHint ?: stringResource(R.string.search_hint)
    var query by remember(visible) { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(query) {
        onSearch?.invoke(query)
    }

    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else {
            val q = query.trim().lowercase()
            options.filter { it.searchText.lowercase().contains(q) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                placeholder = { Text(resolvedSearchHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
                singleLine = true,
            )
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.picker_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { option ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            option.subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

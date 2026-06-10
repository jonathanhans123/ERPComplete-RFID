package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.util.DisplayFormat
import com.google.gson.JsonObject

enum class SortDirection {
    ASC,
    DESC,
}

class TableSortSearchState internal constructor(
    initialSortColumn: Int,
) {
    var searchQuery by mutableStateOf("")
    var sortColumnIndex by mutableIntStateOf(initialSortColumn)
    var sortDirection by mutableStateOf(SortDirection.ASC)
}

@Composable
fun rememberTableSortSearch(initialSortColumn: Int = 0): TableSortSearchState =
    remember { TableSortSearchState(initialSortColumn) }

data class IndexColumnSpec(
    val label: String,
    val weight: Float,
    /** Text used for search + default string sort. */
    val sortText: (JsonObject) -> String,
    val cell: (JsonObject) -> TableCell,
)

fun List<JsonObject>.applyTableSortSearch(
    state: TableSortSearchState,
    columns: List<IndexColumnSpec>,
    extraSearchText: ((JsonObject) -> String)? = null,
): List<JsonObject> {
    val query = state.searchQuery.trim().lowercase()
    val filtered = if (query.isBlank()) {
        this
    } else {
        filter { row ->
            val haystack = buildString {
                columns.forEach { append(it.sortText(row)).append(' ') }
                extraSearchText?.invoke(row)?.let { append(it) }
            }.lowercase()
            haystack.contains(query)
        }
    }
    val col = columns.getOrNull(state.sortColumnIndex) ?: return filtered
    val sorted = filtered.sortedWith { a, b ->
        val av = col.sortText(a).lowercase()
        val bv = col.sortText(b).lowercase()
        av.compareTo(bv)
    }
    return if (state.sortDirection == SortDirection.ASC) sorted else sorted.asReversed()
}

@Composable
fun ListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search list…",
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        singleLine = true,
    )
}

@Composable
fun JsonIndexListTable(
    rows: List<JsonObject>,
    columns: List<IndexColumnSpec>,
    sortSearch: TableSortSearchState,
    emptyText: String,
    searchPlaceholder: String = "Search…",
    loading: Boolean = false,
    loadingMore: Boolean = false,
    hasMore: Boolean = false,
    totalCount: Int? = null,
    onLoadMore: (() -> Unit)? = null,
    onRowClick: ((JsonObject) -> Unit)? = null,
    modifier: Modifier = Modifier,
    extraSearchText: ((JsonObject) -> String)? = null,
    listHeader: (@Composable () -> Unit)? = null,
) {
    val visible = remember(rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        rows.applyTableSortSearch(sortSearch, columns, extraSearchText)
    }
    val sortLabels = columns.map { it.label }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortableCardListToolbar(
            itemCount = totalCount ?: visible.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = searchPlaceholder,
        )
        listHeader?.invoke()
        if (totalCount != null && totalCount > visible.size) {
            Text(
                "Showing ${visible.size} of $totalCount — load more or refine search",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            loading && visible.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            visible.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEach { row ->
                        ErpCard(
                            onClick = if (onRowClick != null) {
                                { onRowClick(row) }
                            } else {
                                null
                            },
                        ) {
                            IndexListCardContent(columns = columns, row = row)
                        }
                    }
                    if (hasMore && onLoadMore != null) {
                        ErpPrimaryButton(
                            text = if (loadingMore) "Loading…" else "Load more",
                            loading = loadingMore,
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexListCardContent(columns: List<IndexColumnSpec>, row: JsonObject) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        columns.firstOrNull()?.let { primary ->
            IndexTableCellValue(primary.cell(row), prominent = true)
        }
        columns.drop(1).forEach { col ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    col.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IndexTableCellValue(col.cell(row), prominent = false)
                }
            }
        }
    }
}

@Composable
private fun IndexTableCellValue(cell: TableCell, prominent: Boolean) {
    when (cell) {
        is TableCell.Text -> Text(
            text = cell.value,
            style = if (prominent) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = when {
                prominent || cell.bold -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            maxLines = if (prominent) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        is TableCell.Status -> StatusChip(cell.raw)
        is TableCell.Date -> Text(
            DisplayFormat.date(cell.raw),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        is TableCell.DateTime -> Text(
            DisplayFormat.dateTime(cell.raw),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SortableCardListToolbar(
    itemCount: Int,
    sortSearch: TableSortSearchState,
    sortLabels: List<String>,
    searchPlaceholder: String = "Search…",
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListSearchField(
            query = sortSearch.searchQuery,
            onQueryChange = { sortSearch.searchQuery = it },
            placeholder = searchPlaceholder,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$itemCount item${if (itemCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Text(
                        "Sort: ${sortLabels.getOrNull(sortSearch.sortColumnIndex) ?: "—"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(
                    onClick = {
                        sortSearch.sortDirection = when (sortSearch.sortDirection) {
                            SortDirection.ASC -> SortDirection.DESC
                            SortDirection.DESC -> SortDirection.ASC
                        }
                    },
                ) {
                    Icon(
                        when (sortSearch.sortDirection) {
                            SortDirection.ASC -> Icons.Default.ArrowUpward
                            SortDirection.DESC -> Icons.Default.ArrowDownward
                        },
                        contentDescription = "Toggle sort direction",
                    )
                }
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sortLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            if (sortSearch.sortColumnIndex == index) {
                                sortSearch.sortDirection = when (sortSearch.sortDirection) {
                                    SortDirection.ASC -> SortDirection.DESC
                                    SortDirection.DESC -> SortDirection.ASC
                                }
                            } else {
                                sortSearch.sortColumnIndex = index
                                sortSearch.sortDirection = SortDirection.ASC
                            }
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

fun List<JsonObject>.applyCardListSortSearch(
    state: TableSortSearchState,
    sortTexts: List<(JsonObject) -> String>,
    extraSearchText: ((JsonObject) -> String)? = null,
): List<JsonObject> {
    val query = state.searchQuery.trim().lowercase()
    val filtered = if (query.isBlank()) {
        this
    } else {
        filter { row ->
            val haystack = buildString {
                sortTexts.forEach { append(it(row)).append(' ') }
                extraSearchText?.invoke(row)?.let { append(it) }
            }.lowercase()
            haystack.contains(query)
        }
    }
    val sorter = sortTexts.getOrNull(state.sortColumnIndex) ?: return filtered
    val sorted = filtered.sortedWith { a, b ->
        sorter(a).lowercase().compareTo(sorter(b).lowercase())
    }
    return if (state.sortDirection == SortDirection.ASC) sorted else sorted.asReversed()
}

@Composable
fun SortableWorkflowTableHeader(
    columns: List<DataTableColumn>,
    sortSearch: TableSortSearchState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        columns.forEachIndexed { index, col ->
            val active = sortSearch.sortColumnIndex == index
            Row(
                Modifier
                    .weight(col.weight)
                    .padding(end = if (index < columns.lastIndex) 6.dp else 0.dp)
                    .clickable {
                        if (sortSearch.sortColumnIndex == index) {
                            sortSearch.sortDirection = when (sortSearch.sortDirection) {
                                SortDirection.ASC -> SortDirection.DESC
                                SortDirection.DESC -> SortDirection.ASC
                            }
                        } else {
                            sortSearch.sortColumnIndex = index
                            sortSearch.sortDirection = SortDirection.ASC
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    col.label,
                    modifier = Modifier.weight(1f),
                    style = workflowTableHeaderStyle().copy(
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = when {
                        !active -> Icons.Default.UnfoldMore
                        sortSearch.sortDirection == SortDirection.ASC -> Icons.Default.ArrowUpward
                        else -> Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                )
            }
        }
    }
}

@Composable
fun SortableWorkflowDataTable(
    columns: List<DataTableColumn>,
    sortSearch: TableSortSearchState,
    rows: List<List<TableCell>>,
    emptyText: String,
    onRowClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    rowBackground: ((Int) -> Color)? = null,
    fillAvailableHeight: Boolean = false,
    contentMinWidth: Dp? = null,
) {
    WorkflowDataTable(
        columns = columns,
        rows = rows,
        emptyText = emptyText,
        onRowClick = onRowClick,
        modifier = modifier,
        loading = loading,
        rowBackground = rowBackground,
        customHeader = { SortableWorkflowTableHeader(columns, sortSearch) },
        fillAvailableHeight = fillAvailableHeight,
        contentMinWidth = contentMinWidth,
    )
}

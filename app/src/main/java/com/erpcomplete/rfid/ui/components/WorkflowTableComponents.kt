package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.util.DisplayFormat

sealed class TableCell {
    data class Text(val value: String, val mono: Boolean = false, val bold: Boolean = false) : TableCell()
    data class Status(val raw: String?) : TableCell()
    data class Date(val raw: String?) : TableCell()
    data class DateTime(val raw: String?) : TableCell()
}

data class DataTableColumn(val label: String, val weight: Float)

@Composable
fun workflowTableHeaderStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
fun WorkflowTableHeader(columns: List<DataTableColumn>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        columns.forEach { col ->
            Text(
                col.label,
                Modifier
                    .weight(col.weight)
                    .padding(end = 6.dp),
                style = workflowTableHeaderStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RowScope.WorkflowTableCell(text: String, weight: Float, mono: Boolean = false, bold: Boolean = false) {
    Box(Modifier.weight(weight)) {
        WorkflowTableTextCell(text, mono = mono, bold = bold)
    }
}

@Composable
private fun WorkflowTableTextCell(
    text: String,
    mono: Boolean = false,
    bold: Boolean = false,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RowScope.RenderTableCell(cell: TableCell, weight: Float) {
    Box(Modifier.weight(weight)) {
        RenderTableCellContent(cell)
    }
}

@Composable
private fun RenderTableCellContent(cell: TableCell) {
    when (cell) {
        is TableCell.Text -> WorkflowTableTextCell(cell.value, mono = cell.mono, bold = cell.bold)
        is TableCell.Status -> Box(Modifier.fillMaxWidth()) { StatusChip(cell.raw) }
        is TableCell.Date -> WorkflowTableTextCell(DisplayFormat.date(cell.raw), maxLines = 1)
        is TableCell.DateTime -> WorkflowTableTextCell(DisplayFormat.dateTime(cell.raw), maxLines = 1)
    }
}

@Composable
fun StatusChip(raw: String?, modifier: Modifier = Modifier) {
    val label = DisplayFormat.status(raw)
    val (bg, fg) = statusColors(raw)
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun statusColors(raw: String?): Pair<Color, Color> {
    val key = raw?.lowercase() ?: return Pair(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return when {
        key.contains("complete") || key.contains("approved") || key == "done" ->
            Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        key.contains("progress") || key.contains("partial") ->
            Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        key.contains("pending") || key.contains("draft") ->
            Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        key.contains("cancel") || key.contains("reject") || key.contains("fail") ->
            Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkflowTableHeaderSlot(
    columns: List<DataTableColumn>,
    customHeader: (@Composable () -> Unit)?,
) {
    if (customHeader != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        ) {
            customHeader()
        }
    } else {
        WorkflowTableHeader(columns)
    }
}

@Composable
private fun WorkflowTableRow(
    index: Int,
    row: List<TableCell>,
    columns: List<DataTableColumn>,
    lastIndex: Int,
    onRowClick: ((Int) -> Unit)?,
    rowBackground: ((Int) -> Color)?,
) {
    val zebra = if (index % 2 == 0) Color.Transparent
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val bg = rowBackground?.invoke(index) ?: zebra
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        row.forEachIndexed { colIndex, cell ->
            val weight = columns.getOrNull(colIndex)?.weight ?: 1f
            Box(
                Modifier
                    .weight(weight)
                    .padding(end = if (colIndex < row.lastIndex) 6.dp else 0.dp),
            ) {
                RenderTableCellContent(cell)
            }
        }
    }
    if (index < lastIndex) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
fun WorkflowDataTable(
    columns: List<DataTableColumn>,
    rows: List<List<TableCell>>,
    emptyText: String,
    onRowClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    rowBackground: ((Int) -> Color)? = null,
    customHeader: (@Composable () -> Unit)? = null,
    /** When true, table body fills remaining height and scrolls (use with [Modifier.weight] on this table). */
    fillAvailableHeight: Boolean = false,
    /** Minimum table width; enables horizontal scroll on narrow screens when set. */
    contentMinWidth: Dp? = null,
) {
    val cardModifier = if (fillAvailableHeight) {
        modifier.fillMaxWidth().fillMaxHeight()
    } else {
        modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        if (fillAvailableHeight) {
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            val scrollModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScroll)
                .then(
                    if (contentMinWidth != null) {
                        Modifier.horizontalScroll(horizontalScroll)
                    } else {
                        Modifier
                    },
                )
            val innerWidth = if (contentMinWidth != null) {
                Modifier.widthIn(min = contentMinWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
            Column(Modifier.fillMaxSize()) {
                Column(scrollModifier) {
                    Column(innerWidth) {
                        WorkflowTableHeaderSlot(columns, customHeader)
                        WorkflowTableRows(
                            columns = columns,
                            rows = rows,
                            emptyText = emptyText,
                            loading = loading,
                            onRowClick = onRowClick,
                            rowBackground = rowBackground,
                        )
                    }
                }
            }
        } else {
            val horizontalScroll = rememberScrollState()
            val innerWidth = if (contentMinWidth != null) {
                Modifier
                    .widthIn(min = contentMinWidth)
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll)
            } else {
                Modifier.fillMaxWidth()
            }
            Column(innerWidth) {
                WorkflowTableHeaderSlot(columns, customHeader)
                WorkflowTableBody(
                    columns = columns,
                    rows = rows,
                    emptyText = emptyText,
                    loading = loading,
                    onRowClick = onRowClick,
                    rowBackground = rowBackground,
                    fillAvailableHeight = false,
                )
            }
        }
    }
}

@Composable
private fun WorkflowTableRows(
    columns: List<DataTableColumn>,
    rows: List<List<TableCell>>,
    emptyText: String,
    loading: Boolean,
    onRowClick: ((Int) -> Unit)?,
    rowBackground: ((Int) -> Color)?,
) {
    when {
        loading && rows.isEmpty() -> TableMessage("Loading…")
        rows.isEmpty() -> TableMessage(emptyText)
        else -> {
            rows.forEachIndexed { index, row ->
                WorkflowTableRow(
                    index = index,
                    row = row,
                    columns = columns,
                    lastIndex = rows.lastIndex,
                    onRowClick = onRowClick,
                    rowBackground = rowBackground,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.WorkflowTableBody(
    columns: List<DataTableColumn>,
    rows: List<List<TableCell>>,
    emptyText: String,
    loading: Boolean,
    onRowClick: ((Int) -> Unit)?,
    rowBackground: ((Int) -> Color)?,
    fillAvailableHeight: Boolean,
) {
    when {
        loading && rows.isEmpty() -> TableMessage("Loading…")
        rows.isEmpty() -> TableMessage(emptyText)
        else -> {
            val scrollState = rememberScrollState()
            val bodyModifier = if (fillAvailableHeight) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            }
            Column(modifier = bodyModifier) {
                rows.forEachIndexed { index, row ->
                    WorkflowTableRow(
                        index = index,
                        row = row,
                        columns = columns,
                        lastIndex = rows.lastIndex,
                        onRowClick = onRowClick,
                        rowBackground = rowBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableMessage(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WorkflowScanPanel(
    tags: List<RfidManager.ScannedTag>,
    resolveMap: Map<String, ScanResolveEntry>,
    onClear: () -> Unit,
    onUnknownClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownCount = tags.count { resolveMap[it.epc.uppercase()]?.status == ScanResolveStatus.UNKNOWN }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${tags.size} scan${if (tags.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unknownCount > 0) {
                    Text(
                        "$unknownCount not in ERP — tap red row to register",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onClear, enabled = tags.isNotEmpty()) {
                androidx.compose.material3.Icon(Icons.Default.ClearAll, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("Clear")
            }
        }
        Text(
            "Hold top trigger to scan",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        ResolvedScanTable(
            rows = tags.take(20).map { tag ->
                val entry = resolveMap[tag.epc.uppercase()]
                ResolvedScanRow(
                    code = tag.epc,
                    typeLabel = "RFID",
                    rssi = tag.rssi?.toString(),
                    entry = entry,
                )
            },
            emptyText = "Scanned tags appear here.",
            onUnknownClick = onUnknownClick,
        )
    }
}

data class ResolvedScanRow(
    val code: String,
    val typeLabel: String,
    val rssi: String? = null,
    val entry: ScanResolveEntry? = null,
)

@Composable
fun ResolvedScanTable(
    rows: List<ResolvedScanRow>,
    emptyText: String,
    onUnknownClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        WorkflowTableHeader(
            listOf(
                DataTableColumn("Code", 1.3f),
                DataTableColumn("ERP", 1f),
                DataTableColumn("Type", 0.45f),
            ),
        )
        if (rows.isEmpty()) {
            TableMessage(emptyText)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                itemsIndexed(rows, key = { _, r -> r.code }) { index, row ->
                    val status = row.entry?.status ?: ScanResolveStatus.PENDING
                    val bg = when (status) {
                        ScanResolveStatus.UNKNOWN -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        ScanResolveStatus.REGISTERED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                        ScanResolveStatus.PENDING -> Color.Transparent
                    }
                    val clickable = status == ScanResolveStatus.UNKNOWN
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .then(if (clickable) Modifier.clickable { onUnknownClick(row.code) } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        WorkflowTableCell(row.code, 1.3f, mono = true)
                        WorkflowTableCell(
                            when (status) {
                                ScanResolveStatus.PENDING -> "…"
                                ScanResolveStatus.UNKNOWN -> "Not registered"
                                ScanResolveStatus.REGISTERED -> listOfNotNull(
                                    row.entry?.productName,
                                    row.entry?.variationLabel,
                                ).joinToString(" · ").ifBlank { "Linked" }
                            },
                            1f,
                        )
                        WorkflowTableCell(row.typeLabel, 0.45f)
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}

/** @deprecated Use [WorkflowDataTable] with [TableCell] */
@Composable
fun WorkflowListTable(
    columns: List<Pair<String, Float>>,
    rows: List<List<String>>,
    emptyText: String,
    onRowClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    WorkflowDataTable(
        columns = columns.map { DataTableColumn(it.first, it.second) },
        rows = rows.map { row -> row.map { TableCell.Text(it) } },
        emptyText = emptyText,
        onRowClick = onRowClick,
        modifier = modifier,
        loading = loading,
    )
}

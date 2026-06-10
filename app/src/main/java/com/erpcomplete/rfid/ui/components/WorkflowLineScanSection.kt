package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.util.DisplayFormat

data class ScanMatchColors(
    val unknown: Color,
    val unmatched: Color,
    val matched: Color,
    val pending: Color = Color.Transparent,
) {
    fun forStatus(status: ScanMatchStatus): Color = when (status) {
        ScanMatchStatus.UNKNOWN -> unknown
        ScanMatchStatus.UNMATCHED -> unmatched
        ScanMatchStatus.MATCHED, ScanMatchStatus.FORCED -> matched
        ScanMatchStatus.PENDING -> pending
    }
}

@Composable
fun rememberScanMatchColors(): ScanMatchColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        ScanMatchColors(
            unknown = scheme.errorContainer.copy(alpha = 0.72f),
            unmatched = scheme.tertiaryContainer.copy(alpha = 0.78f),
            matched = scheme.primaryContainer.copy(alpha = 0.82f),
        )
    }
}

@Composable
fun WorkflowLineScanSection(
    container: AppContainer,
    tags: List<RfidManager.ScannedTag>,
    lines: List<WorkflowMatchLine>,
    onIncrementLine: ((lineKey: String, entry: ScanResolveEntry) -> Unit)?,
    onLineHighlightsChanged: (Map<String, ScanMatchStatus>) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var registerCode by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var barcodeInput by remember { mutableStateOf("") }
    var unmatchedCode by remember { mutableStateOf<String?>(null) }
    var forceLinePickerFor by remember { mutableStateOf<String?>(null) }

    val forcedLineKeys = remember { mutableStateMapOf<String, String>() }
    val autoIncremented = remember { mutableStateMapOf<String, Boolean>() }

    val resolveMap = rememberScanResolver(container.api, tags.map { it.epc }, refreshKey)
    val scanColors = rememberScanMatchColors()

    LaunchedEffect(tags, resolveMap, lines, forcedLineKeys.toMap()) {
        val lineHighlights = mutableMapOf<String, ScanMatchStatus>()

        tags.forEach { tag ->
            val code = tag.epc.uppercase()
            val entry = resolveMap[code] ?: return@forEach
            val forcedKey = forcedLineKeys[code]
            val status = WorkflowLineMatcher.matchStatus(entry, lines, forcedKey)

            if (
                (status == ScanMatchStatus.MATCHED || status == ScanMatchStatus.FORCED) &&
                autoIncremented[code] != true
            ) {
                val matched = WorkflowLineMatcher.findMatchingLine(entry, lines, forcedKey)
                if (matched != null) {
                    autoIncremented[code] = true
                    if (onIncrementLine != null && shouldIncrementQtyOnScan(matched, entry)) {
                        onIncrementLine(matched.key, entry)
                    }
                    lineHighlights[matched.key] = ScanMatchStatus.MATCHED
                }
            } else if (status == ScanMatchStatus.MATCHED || status == ScanMatchStatus.FORCED) {
                WorkflowLineMatcher.findMatchingLine(entry, lines, forcedKey)?.key?.let { key ->
                    lineHighlights[key] = ScanMatchStatus.MATCHED
                }
            }
        }
        onLineHighlightsChanged(lineHighlights)
    }

    LaunchedEffect(tags.size) {
        val active = tags.map { it.epc.uppercase() }.toSet()
        autoIncremented.keys.toList().filter { it !in active }.forEach { autoIncremented.remove(it) }
        forcedLineKeys.keys.toList().filter { it !in active }.forEach { forcedLineKeys.remove(it) }
    }

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
                val unmatched = tags.count {
                    WorkflowLineMatcher.matchStatus(resolveMap[it.epc.uppercase()], lines, forcedLineKeys[it.epc.uppercase()]) ==
                        ScanMatchStatus.UNMATCHED
                }
                val unknown = tags.count {
                    resolveMap[it.epc.uppercase()]?.status == ScanResolveStatus.UNKNOWN
                }
                if (unknown > 0) {
                    Text(
                        "$unknown not in ERP — tap red row to map",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (unmatched > 0) {
                    Text(
                        "$unmatched not on lines — tap yellow to add or remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            TextButton(onClick = {
                onClear()
                forcedLineKeys.clear()
                autoIncremented.clear()
                onLineHighlightsChanged(emptyMap())
            }, enabled = tags.isNotEmpty()) {
                Icon(Icons.Default.ClearAll, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("Clear")
            }
        }
        Text(
            "Top trigger = RFID · bottom trigger or field = barcode (same table)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Roll products: one tag per roll — scan confirms the roll; enter length manually.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = barcodeInput,
            onValueChange = { barcodeInput = it },
            label = { Text("Barcode") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val code = barcodeInput.trim()
                if (code.isNotBlank()) {
                    container.rfidManager.recordWorkflowBarcode(code)
                    barcodeInput = ""
                }
            }),
        )
        Spacer(Modifier.height(8.dp))
        LineMatchScanTable(
            scanColors = scanColors,
            rows = tags.take(30).map { tag ->
                val code = tag.epc.uppercase()
                val entry = resolveMap[code]
                val status = WorkflowLineMatcher.matchStatus(entry, lines, forcedLineKeys[code])
                LineMatchScanRow(
                    code = tag.epc,
                    typeLabel = if (tag.type == RfidManager.ScanType.BARCODE) "Barcode" else "RFID",
                    entry = entry,
                    matchStatus = status,
                    matchedLineLabel = WorkflowLineMatcher.findMatchingLine(entry ?: ScanResolveEntry(ScanResolveStatus.PENDING), lines, forcedLineKeys[code])
                        ?.label,
                )
            },
            emptyText = "Scanned tags appear here — green = matched, yellow = wrong line, red = unknown.",
            onUnknownClick = { registerCode = it },
            onUnmatchedClick = { unmatchedCode = it },
        )
    }

    TagRegistrationSheet(
        visible = registerCode != null,
        code = registerCode,
        container = container,
        onDismiss = { registerCode = null },
        onRegistered = { code ->
            refreshKey++
            registerCode = null
            autoIncremented.remove(code.uppercase())
        },
    )

    val unmatchedEntry = unmatchedCode?.let { resolveMap[it.uppercase()] }
    if (unmatchedCode != null && unmatchedEntry != null) {
        AlertDialog(
            onDismissRequest = { unmatchedCode = null },
            title = { Text("Product not on lines") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(unmatchedCode!!, style = MaterialTheme.typography.labelMedium)
                    Text(
                        listOfNotNull(
                            unmatchedEntry.productName,
                            unmatchedEntry.variationLabel,
                            unmatchedEntry.rollLabel?.let { "Roll $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Add this scan to a line anyway, or remove it from the session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    forceLinePickerFor = unmatchedCode
                    unmatchedCode = null
                }) { Text("Add to line…") }
            },
            dismissButton = {
                TextButton(onClick = {
                    container.rfidManager.removeScannedTag(unmatchedCode!!)
                    forcedLineKeys.remove(unmatchedCode!!.uppercase())
                    autoIncremented.remove(unmatchedCode!!.uppercase())
                    unmatchedCode = null
                }) { Text("Remove scan") }
            },
        )
    }

    if (forceLinePickerFor != null && lines.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { forceLinePickerFor = null },
            title = { Text("Choose line") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { line ->
                        TextButton(
                            onClick = {
                                val code = forceLinePickerFor!!.uppercase()
                                forcedLineKeys[code] = line.key
                                autoIncremented.remove(code)
                                forceLinePickerFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(line.label.ifBlank { "Line ${line.key}" }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { forceLinePickerFor = null }) { Text("Cancel") }
            },
        )
    }
}

private data class LineMatchScanRow(
    val code: String,
    val typeLabel: String,
    val entry: ScanResolveEntry?,
    val matchStatus: ScanMatchStatus,
    val matchedLineLabel: String?,
)

@Composable
private fun LineMatchScanTable(
    scanColors: ScanMatchColors,
    rows: List<LineMatchScanRow>,
    emptyText: String,
    onUnknownClick: (String) -> Unit,
    onUnmatchedClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        WorkflowTableHeader(
            listOf(
                DataTableColumn("Code", 1.2f),
                DataTableColumn("ERP / match", 1.1f),
                DataTableColumn("Type", 0.4f),
            ),
        )
        if (rows.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                itemsIndexed(rows, key = { _, r -> r.code }) { index, row ->
                    val bg = scanColors.forStatus(row.matchStatus)
                    val clickable = when (row.matchStatus) {
                        ScanMatchStatus.UNKNOWN -> Modifier.clickable { onUnknownClick(row.code) }
                        ScanMatchStatus.UNMATCHED -> Modifier.clickable { onUnmatchedClick(row.code) }
                        else -> Modifier
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .then(clickable)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        WorkflowTableCell(row.code, 1.2f, mono = true)
                        WorkflowTableCell(
                            when (row.matchStatus) {
                                ScanMatchStatus.PENDING -> "…"
                                ScanMatchStatus.UNKNOWN -> "Not registered — tap to map"
                                ScanMatchStatus.UNMATCHED -> listOfNotNull(
                                    row.entry?.productName,
                                    row.entry?.variationLabel,
                                ).joinToString(" · ").ifBlank { "No matching line" }
                                ScanMatchStatus.MATCHED, ScanMatchStatus.FORCED -> buildString {
                                    append(
                                        listOfNotNull(
                                            row.entry?.productName,
                                            row.entry?.variationLabel,
                                            row.entry?.rollLabel?.let { "Roll $it" },
                                        ).joinToString(" · ").ifBlank { "Matched" },
                                    )
                                    row.matchedLineLabel?.let { append("\n→ ").append(it) }
                                    if (row.entry?.isRoll == true) {
                                        append("\nEnter length manually")
                                    }
                                }
                            },
                            1.1f,
                        )
                        WorkflowTableCell(row.typeLabel, 0.4f)
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}

/** Increment a qty text field by scan delta. */
fun incrementQtyField(current: String, delta: Double): String {
    val base = current.toDoubleOrNull() ?: 0.0
    return DisplayFormat.qty(base + delta)
}

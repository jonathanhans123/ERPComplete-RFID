package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.ui.util.UiStrings
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.boolean
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.onHandQuantity
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.productSku
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.rollFillStatus
import com.erpcomplete.rfid.util.WorkflowJson.rollNominalQuantity
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.google.gson.JsonObject

@Composable
fun UnmarkedLocationCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemCount: Int? = null,
) {
    ErpCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.label_unmarked_location), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.location_unmarked_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                itemCount?.let { count ->
                    Text(
                        pluralStringResource(R.plurals.location_line_count, count, count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            InfoPill(stringResource(R.string.location_no_bin))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarehouseLocationPickerCard(
    location: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emDash = stringResource(R.string.display_empty)
    val label = WorkflowJson.locationLabel(location).ifBlank { location.string("zone_name") ?: emDash }
    val zone = location.string("zone_name")?.takeIf { it.isNotBlank() }
    val zoneCode = location.string("zone_code")?.takeIf { it.isNotBlank() }
    val locationType = location.string("location_type")?.let { UiStrings.locationType(it) }
    val active = location.boolean("is_active") != false

    ErpCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                listOfNotNull(zone, zoneCode?.let { stringResource(R.string.location_zone_code, it) })
                    .distinct()
                    .joinToString(" · ")
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }
            StatusChip(if (active) "active" else "inactive")
        }
        locationType?.let {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(it)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationStockLineCard(
    stock: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rollPill = stringResource(R.string.product_type_roll)
    val standardPill = stringResource(R.string.product_type_standard)
    val isRoll = stock.isRollStockLine()
    val unit = stock.quantityUnitSuffix()
    val onHand = stock.onHandQuantity()
    val onHandLabel = formatQtyWithUnit(DisplayFormat.qty(onHand), unit, isRoll)
    val variation = stock.variationLabel()
    val sku = stock.productSku()
    val rollNumber = stock.string("roll_number")?.takeIf { it.isNotBlank() }
    val fillStatus = stock.rollFillStatus()

    ErpCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stock.productName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sku.isNotBlank() || variation.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            sku.takeIf { it.isNotBlank() },
                            variation.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isRoll && fillStatus != null) {
                StatusChip(fillStatus)
            }
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoPill(if (isRoll) rollPill else standardPill)
            rollNumber?.let { InfoPill(stringResource(R.string.roll_number_pill, it)) }
            stock.string("batch_number")?.takeIf { it.isNotBlank() }?.let { InfoPill(stringResource(R.string.batch_pill, it)) }
        }

        Spacer(Modifier.height(10.dp))
        if (isRoll) {
            val nominal = stock.rollNominalQuantity()
            Text(
                if (nominal != null) {
                    val nominalLabel = formatQtyWithUnit(DisplayFormat.qty(nominal), unit, isRoll = true)
                    stringResource(R.string.stock_roll_on_hand_nominal, onHandLabel, nominalLabel)
                } else {
                    stringResource(R.string.stock_on_hand, onHandLabel)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            fillStatus?.let {
                Text(
                    if (it == "full") {
                        stringResource(R.string.roll_full_description)
                    } else {
                        stringResource(R.string.roll_partial_description)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                stringResource(R.string.stock_on_hand_label, onHandLabel),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationStockDetailSheet(
    visible: Boolean,
    stock: JsonObject?,
    locationLabel: String,
    canAdjust: Boolean = true,
    canRelocate: Boolean = true,
    onDismiss: () -> Unit,
    onAdjust: (JsonObject) -> Unit,
    onRelocate: ((JsonObject) -> Unit)? = null,
) {
    if (!visible || stock == null) return

    val rollType = stringResource(R.string.product_type_roll)
    val standardType = stringResource(R.string.product_type_standard)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isRoll = stock.isRollStockLine()
    val unit = stock.quantityUnitSuffix()
    val onHand = stock.onHandQuantity()
    val fillStatus = stock.rollFillStatus()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.location_stock_detail_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                locationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            ErpCard(onClick = null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stock.productName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    stockDetailRow(stringResource(R.string.label_sku), stock.productSku().takeIf { it.isNotBlank() })
                    stockDetailRow(stringResource(R.string.label_variation), stock.variationLabel().takeIf { it.isNotBlank() })
                    stockDetailRow(stringResource(R.string.label_product_type), if (isRoll) rollType else standardType)
                    if (isRoll) {
                        stockDetailRow(stringResource(R.string.label_roll_number), stock.string("roll_number"))
                        stockDetailRow(
                            stringResource(R.string.label_on_hand_length),
                            formatQtyWithUnit(DisplayFormat.qty(onHand), unit, isRoll = true),
                        )
                        stock.rollNominalQuantity()?.let { nominal ->
                            stockDetailRow(
                                stringResource(R.string.label_nominal_length),
                                formatQtyWithUnit(DisplayFormat.qty(nominal), unit, isRoll = true),
                            )
                        }
                        stockDetailRow(
                            stringResource(R.string.label_roll_status),
                            when (fillStatus) {
                                "full" -> stringResource(R.string.roll_status_full)
                                "partial" -> stringResource(R.string.roll_status_partial)
                                else -> null
                            },
                        )
                    } else {
                        stockDetailRow(
                            stringResource(R.string.label_quantity_on_hand),
                            formatQtyWithUnit(DisplayFormat.qty(onHand), unit, isRoll = false),
                        )
                    }
                    stockDetailRow(stringResource(R.string.label_batch), stock.string("batch_number"))
                    stock.string("stock_type")?.let {
                        stockDetailRow(stringResource(R.string.label_stock_type), UiStrings.apiStatus(it))
                    }
                    stock.double("unit_price")?.let { stockDetailRow(stringResource(R.string.label_unit_price), DisplayFormat.qty(it)) }
                    stock.string("updated_at")?.let { stockDetailRow(stringResource(R.string.label_last_updated), DisplayFormat.dateTime(it)) }
                    stock.long("id")?.let { stockDetailRow(stringResource(R.string.label_stock_line_id), stringResource(R.string.stock_line_id_value, it)) }
                }
            }
            if (canAdjust) {
                ErpPrimaryButton(
                    text = stringResource(R.string.action_adjust_stock),
                    onClick = { onAdjust(stock) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (canRelocate && onRelocate != null) {
                OutlinedButton(
                    onClick = { onRelocate(stock) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_relocate_bin))
                }
            }
            if (!canAdjust && !canRelocate) {
                Text(
                    stringResource(R.string.location_stock_no_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun stockDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

package com.erpcomplete.rfid.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.ui.util.UiStrings
import com.erpcomplete.rfid.util.DisplayFormat

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.tagInfoNestedMap(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.tagInfoListOfMaps(key: String): List<Map<String, Any?>> {
    val raw = this[key] as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? Map<String, Any?> }
}

fun Map<String, Any?>?.tagInfoString(key: String): String? =
    this?.get(key)?.toString()?.takeIf { it.isNotBlank() }

fun Map<String, Any?>.tagLocationUnassigned(): Boolean {
    if (this["location_unassigned"] == true) return true
    val location = tagInfoNestedMap("warehouse_location")
    if (location?.get("unassigned") == true) return true
    val stock = tagInfoNestedMap("stock")
    val warehouse = tagInfoNestedMap("warehouse")
    return stock != null && warehouse != null && location == null
}

fun Map<String, Any?>.tagLocationLabel(context: Context): String? {
    val location = tagInfoNestedMap("warehouse_location")
    location?.tagInfoString("name")?.let { return it }
    if (tagLocationUnassigned()) return context.getString(R.string.label_unmarked_location)
    return null
}

@Composable
fun TagInfoDetailContent(
    info: Map<String, Any?>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val product = info.tagInfoNestedMap("product")
    val variation = info.tagInfoNestedMap("variation")
    val stock = info.tagInfoNestedMap("stock")
    val warehouse = info.tagInfoNestedMap("warehouse")
    val location = info.tagInfoNestedMap("warehouse_location")
    val descriptors = info.tagInfoListOfMaps("variation_descriptors")
    val outputLabel = info.tagInfoNestedMap("output_label")
    val locationLabel = info.tagLocationLabel(context)
    val locationUnassigned = info.tagLocationUnassigned()

    Column(modifier) {
        TagInfoDetailSection(stringResource(R.string.tag_info_section_product), compact) {
            TagInfoDetailLine(stringResource(R.string.label_name), product?.tagInfoString("name"))
            TagInfoDetailLine(stringResource(R.string.label_sku), product?.tagInfoString("sku"))
        }

        if (variation != null || descriptors.isNotEmpty()) {
            TagInfoDetailSection(stringResource(R.string.tag_info_section_variation), compact) {
                TagInfoDetailLine(stringResource(R.string.label_name), variation?.tagInfoString("name") ?: variation?.tagInfoString("value"))
                TagInfoDetailLine(stringResource(R.string.label_sku), variation?.tagInfoString("sku"))
                descriptors.forEach { d ->
                    TagInfoDetailLine(d.tagInfoString("name") ?: stringResource(R.string.label_option), d.tagInfoString("value"))
                }
            }
        }

        if (stock != null) {
            TagInfoDetailSection(stringResource(R.string.tag_info_section_stock), compact) {
                val isRoll = info.tagInfoString("product_type") == "roll"
                    || stock["is_roll_product"] == true
                    || !stock.tagInfoString("roll_number").isNullOrBlank()
                    || (stock["roll_length"]?.toString()?.toDoubleOrNull() ?: 0.0) > 0.0
                if (isRoll) {
                    TagInfoDetailLine(stringResource(R.string.label_roll_number_short), stock.tagInfoString("roll_number"))
                    val unit = info.tagInfoString("quantity_unit_suffix")
                    val rollLength = stock["roll_length"]?.toString()
                    TagInfoDetailLine(
                        stringResource(R.string.label_on_hand),
                        if (!rollLength.isNullOrBlank() && !unit.isNullOrBlank()) "$rollLength $unit" else rollLength,
                    )
                    val nominal = stock["quantity"]?.toString()
                    if (!nominal.isNullOrBlank()) {
                        TagInfoDetailLine(
                            stringResource(R.string.label_nominal),
                            if (!unit.isNullOrBlank()) "$nominal $unit" else nominal,
                        )
                    }
                    stock.tagInfoString("roll_fill_status")?.let { fill ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.label_fill),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatusChip(fill)
                        }
                    }
                } else {
                    TagInfoDetailLine(stringResource(R.string.label_quantity), stock["quantity"]?.toString())
                }
                TagInfoDetailLine(stringResource(R.string.label_batch), stock.tagInfoString("batch_number"))
                stock.tagInfoString("stock_type")?.let { UiStrings.apiStatus(it) }?.let {
                    TagInfoDetailLine(stringResource(R.string.label_type), it)
                }
            }
        }

        if (warehouse != null || locationLabel != null) {
            TagInfoDetailSection(stringResource(R.string.tag_info_section_location), compact) {
                TagInfoDetailLine(stringResource(R.string.label_warehouse), warehouse?.tagInfoString("name"))
                TagInfoDetailLine(stringResource(R.string.label_location), locationLabel)
                if (!locationUnassigned) {
                    TagInfoDetailLine(stringResource(R.string.label_location_barcode), location?.tagInfoString("barcode"))
                }
            }
        }

        if (outputLabel != null) {
            TagInfoDetailSection(stringResource(R.string.tag_info_section_label), compact) {
                TagInfoDetailLine(stringResource(R.string.label_barcode), outputLabel.tagInfoString("barcode_value"))
                TagInfoDetailLine(
                    stringResource(R.string.label_status),
                    UiStrings.apiStatus(outputLabel.tagInfoString("status")),
                )
            }
        }

        if (!compact) {
            TagInfoDetailSection(stringResource(R.string.tag_info_section_tag), compact) {
                TagInfoDetailLine(stringResource(R.string.label_epc), info.tagInfoString("epc"))
                TagInfoDetailLine(
                    stringResource(R.string.label_status),
                    UiStrings.apiStatus(info.tagInfoString("status")),
                )
            }
        }
    }
}

@Composable
private fun TagInfoDetailSection(
    title: String,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
    ErpCard {
        Column(
            Modifier.padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TagInfoDetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

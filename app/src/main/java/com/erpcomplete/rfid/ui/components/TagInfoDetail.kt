package com.erpcomplete.rfid.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

fun Map<String, Any?>.tagLocationLabel(): String? {
    val location = tagInfoNestedMap("warehouse_location")
    location?.tagInfoString("name")?.let { return it }
    if (tagLocationUnassigned()) return "Unmarked location"
    return null
}

@Composable
fun TagInfoDetailContent(
    info: Map<String, Any?>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val product = info.tagInfoNestedMap("product")
    val variation = info.tagInfoNestedMap("variation")
    val stock = info.tagInfoNestedMap("stock")
    val warehouse = info.tagInfoNestedMap("warehouse")
    val location = info.tagInfoNestedMap("warehouse_location")
    val descriptors = info.tagInfoListOfMaps("variation_descriptors")
    val outputLabel = info.tagInfoNestedMap("output_label")
    val locationLabel = info.tagLocationLabel()
    val locationUnassigned = info.tagLocationUnassigned()

    Column(modifier) {
        TagInfoDetailSection("Product", compact) {
            TagInfoDetailLine("Name", product?.tagInfoString("name"))
            TagInfoDetailLine("SKU", product?.tagInfoString("sku"))
        }

        if (variation != null || descriptors.isNotEmpty()) {
            TagInfoDetailSection("Variation", compact) {
                TagInfoDetailLine("Name", variation?.tagInfoString("name") ?: variation?.tagInfoString("value"))
                TagInfoDetailLine("SKU", variation?.tagInfoString("sku"))
                descriptors.forEach { d ->
                    TagInfoDetailLine(d.tagInfoString("name") ?: "Option", d.tagInfoString("value"))
                }
            }
        }

        if (stock != null) {
            TagInfoDetailSection("Stock", compact) {
                val isRoll = info.tagInfoString("product_type") == "roll"
                    || stock["is_roll_product"] == true
                    || !stock.tagInfoString("roll_number").isNullOrBlank()
                    || (stock["roll_length"]?.toString()?.toDoubleOrNull() ?: 0.0) > 0.0
                if (isRoll) {
                    TagInfoDetailLine("Roll #", stock.tagInfoString("roll_number"))
                    val unit = info.tagInfoString("quantity_unit_suffix")
                    val rollLength = stock["roll_length"]?.toString()
                    TagInfoDetailLine(
                        "On hand",
                        if (!rollLength.isNullOrBlank() && !unit.isNullOrBlank()) "$rollLength $unit" else rollLength,
                    )
                    val nominal = stock["quantity"]?.toString()
                    if (!nominal.isNullOrBlank()) {
                        TagInfoDetailLine(
                            "Nominal",
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
                                "Fill",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatusChip(DisplayFormat.status(fill))
                        }
                    }
                } else {
                    TagInfoDetailLine("Quantity", stock["quantity"]?.toString())
                }
                TagInfoDetailLine("Batch", stock.tagInfoString("batch_number"))
                TagInfoDetailLine("Type", stock.tagInfoString("stock_type"))
            }
        }

        if (warehouse != null || locationLabel != null) {
            TagInfoDetailSection("Location", compact) {
                TagInfoDetailLine("Warehouse", warehouse?.tagInfoString("name"))
                TagInfoDetailLine("Location", locationLabel)
                if (!locationUnassigned) {
                    TagInfoDetailLine("Location barcode", location?.tagInfoString("barcode"))
                }
            }
        }

        if (outputLabel != null) {
            TagInfoDetailSection("Label", compact) {
                TagInfoDetailLine("Barcode", outputLabel.tagInfoString("barcode_value"))
                TagInfoDetailLine("Status", outputLabel.tagInfoString("status"))
            }
        }

        if (!compact) {
            TagInfoDetailSection("Tag", compact) {
                TagInfoDetailLine("EPC", info.tagInfoString("epc"))
                TagInfoDetailLine("Status", info.tagInfoString("status"))
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

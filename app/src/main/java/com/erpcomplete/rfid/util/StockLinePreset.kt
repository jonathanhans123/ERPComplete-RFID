package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.ui.navigation.Routes
import com.erpcomplete.rfid.ui.util.UiStrings
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.onHandQuantity
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.google.gson.JsonObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Preset for stock adjustment or relocation forms (from RFID tag or location stock line). */
data class StockLinePreset(
    val productId: Long,
    val productLabel: String,
    val variationValueId: Long? = null,
    val variationLabel: String? = null,
    val locationId: Long? = null,
    val currentQty: Double? = null,
    val batchNumber: String? = null,
    val rollNumber: String? = null,
    val isRoll: Boolean = false,
    val quantityUnitSuffix: String? = null,
)

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.toStockLinePreset(): StockLinePreset? {
    val product = this["product"] as? Map<String, Any?> ?: return null
    val productId = product["id"]?.toString()?.toLongOrNull() ?: return null
    val productLabel = product["name"]?.toString()?.takeIf { it.isNotBlank() }
        ?: UiStrings.productFallback(productId)
    val variation = this["variation"] as? Map<String, Any?>
    val variationId = variation?.get("id")?.toString()?.toLongOrNull()
    val variationLabel = variation?.get("value")?.toString()
        ?: variation?.get("name")?.toString()
    val stock = this["stock"] as? Map<String, Any?>
    val isRoll = this["product_type"]?.toString() == "roll"
        || stock?.get("is_roll_product") == true
        || !stock?.get("roll_number")?.toString().isNullOrBlank()
    val rollNumber = stock?.get("roll_number")?.toString()?.takeIf { it.isNotBlank() }
    val batchNumber = stock?.get("batch_number")?.toString()?.takeIf { it.isNotBlank() }
    val qty = if (isRoll) {
        stock?.get("roll_length")?.toString()?.toDoubleOrNull()
    } else {
        stock?.get("quantity")?.toString()?.toDoubleOrNull()
    }
    val unit = this["quantity_unit_suffix"]?.toString()?.takeIf { it.isNotBlank() }
    val location = this["warehouse_location"] as? Map<String, Any?>
    val locationId = when {
        this["location_unassigned"] == true -> UNMARKED_STOCK_LOCATION_ID
        location?.get("unassigned") == true -> UNMARKED_STOCK_LOCATION_ID
        location != null -> location["id"]?.toString()?.toLongOrNull() ?: UNMARKED_STOCK_LOCATION_ID
        stock != null -> UNMARKED_STOCK_LOCATION_ID
        else -> null
    }
    return StockLinePreset(
        productId = productId,
        productLabel = productLabel,
        variationValueId = variationId,
        variationLabel = variationLabel,
        locationId = locationId,
        currentQty = qty,
        batchNumber = batchNumber,
        rollNumber = rollNumber,
        isRoll = isRoll,
        quantityUnitSuffix = unit,
    )
}

fun JsonObject.toStockLinePreset(fallbackLocationId: Long): StockLinePreset? {
    val productId = long("product_id") ?: return null
    return StockLinePreset(
        productId = productId,
        productLabel = productName(),
        variationValueId = long("variation_value_id"),
        variationLabel = variationLabel().takeIf { it.isNotBlank() },
        locationId = fallbackLocationId,
        currentQty = onHandQuantity(),
        batchNumber = string("batch_number"),
        rollNumber = string("roll_number"),
        isRoll = isRollStockLine(),
        quantityUnitSuffix = quantityUnitSuffix(),
    )
}

/** Sentinel for stock with null `warehouse_location_id`. */
const val UNMARKED_STOCK_LOCATION_ID = -1L

fun buildInventoryDeepLinkUri(flow: String, preset: StockLinePreset): String {
    fun enc(value: String?) = value?.let {
        URLEncoder.encode(it, StandardCharsets.UTF_8.name())
    }

    val parts = mutableListOf(
        "${Routes.INVENTORY}?flow=$flow",
        "productId=${preset.productId}",
    )
    preset.variationValueId?.let { parts.add("variationId=$it") }
    preset.locationId?.let { parts.add("locationId=$it") }
    preset.currentQty?.let { parts.add("currentQty=$it") }
    preset.isRoll.takeIf { it }?.let { parts.add("isRoll=1") }
    enc(preset.productLabel)?.let { parts.add("productName=$it") }
    enc(preset.variationLabel)?.let { parts.add("variationName=$it") }
    enc(preset.batchNumber)?.let { parts.add("batchNumber=$it") }
    enc(preset.rollNumber)?.let { parts.add("rollNumber=$it") }
    enc(preset.quantityUnitSuffix)?.let { parts.add("unit=$it") }
    return parts.joinToString("&")
}

fun parseInventoryDeepLink(
    flow: String?,
    productId: Long,
    variationId: Long,
    locationId: Long,
    currentQty: Double?,
    isRoll: Boolean,
    productName: String?,
    variationName: String?,
    batchNumber: String?,
    rollNumber: String?,
    unit: String?,
): StockLinePreset? {
    if (flow.isNullOrBlank() || productId <= 0L) return null
    return StockLinePreset(
        productId = productId,
        productLabel = productName?.takeIf { it.isNotBlank() } ?: UiStrings.productFallback(productId),
        variationValueId = variationId.takeIf { it > 0L },
        variationLabel = variationName,
        locationId = locationId.takeIf { it != 0L },
        currentQty = currentQty,
        batchNumber = batchNumber,
        rollNumber = rollNumber,
        isRoll = isRoll,
        quantityUnitSuffix = unit,
    )
}

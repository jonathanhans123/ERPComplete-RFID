package com.erpcomplete.rfid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.remote.ErpApiService
import com.erpcomplete.rfid.data.remote.ResolveBulkRequest
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.StatusMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay

enum class ScanResolveStatus {
    PENDING,
    UNKNOWN,
    REGISTERED,
}

data class ScanResolveEntry(
    val status: ScanResolveStatus,
    val productName: String? = null,
    val productSku: String? = null,
    val productId: Long? = null,
    val variationValueId: Long? = null,
    val variationLabel: String? = null,
    val rollLabel: String? = null,
    val rollNumber: String? = null,
    val rollLength: Double? = null,
    val quantityUnitSuffix: String? = null,
    val isRoll: Boolean = false,
    val detailInfo: Map<String, Any?>? = null,
)

@Composable
fun rememberScanResolver(
    api: ErpApiService,
    codes: List<String>,
    refreshKey: Int = 0,
    debounceMs: Long = 350,
): Map<String, ScanResolveEntry> {
    val entries = remember { mutableStateMapOf<String, ScanResolveEntry>() }
    var generation by remember { mutableStateOf(0) }

    LaunchedEffect(codes, refreshKey) {
        val normalized = codes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        normalized.forEach { code ->
            if (!entries.containsKey(code)) {
                entries[code] = ScanResolveEntry(ScanResolveStatus.PENDING)
            }
        }
        entries.keys.toList().filter { it !in normalized }.forEach { entries.remove(it) }
        if (normalized.isEmpty()) return@LaunchedEffect

        val myGen = generation + 1
        generation = myGen
        delay(debounceMs)
        if (generation != myGen) return@LaunchedEffect

        runCatching {
            normalized.chunked(RESOLVE_CHUNK_SIZE).forEach { chunk ->
                val res = api.resolveBulk(ResolveBulkRequest(chunk))
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                val results = res.body()?.data?.results ?: emptyList()
                results.forEach { row ->
                    val epc = row.epc?.trim()?.uppercase() ?: return@forEach
                    if (row.resolved == true) {
                        entries[epc] = entryFromResolveInfo(row.info)
                    } else {
                        entries[epc] = ScanResolveEntry(status = ScanResolveStatus.UNKNOWN)
                    }
                }
            }
        }.onFailure {
            // Keep PENDING on transient errors; only mark unknown when response was parsed empty
            if (!StatusMessage.isOfflineSyncMessage(it.message)) {
                normalized.forEach { code ->
                    if (entries[code]?.status == ScanResolveStatus.PENDING) {
                        entries[code] = ScanResolveEntry(ScanResolveStatus.UNKNOWN)
                    }
                }
            }
        }
    }

    return entries
}

private fun entryFromResolveInfo(info: Map<String, Any?>?): ScanResolveEntry =
    ScanResolveEntry(
        status = ScanResolveStatus.REGISTERED,
        productName = resolveProductName(info),
        productSku = resolveProductSku(info),
        productId = resolveProductId(info),
        variationValueId = resolveVariationValueId(info),
        variationLabel = resolveVariationLabel(info),
        rollLabel = resolveRollLabel(info),
        rollNumber = resolveRollNumber(info),
        rollLength = resolveRollLength(info),
        quantityUnitSuffix = resolveQuantityUnitSuffix(info),
        isRoll = resolveIsRoll(info),
        detailInfo = info,
    )

private fun resolveProductId(info: Map<String, Any?>?): Long? =
    info?.get("product_id")?.toString()?.toLongOrNull()

private fun resolveVariationValueId(info: Map<String, Any?>?): Long? =
    info?.get("variation_value_id")?.toString()?.toLongOrNull()

@Suppress("UNCHECKED_CAST")
private fun resolveRollNumber(info: Map<String, Any?>?): String? {
    val stock = info?.get("stock") as? Map<*, *>
    return stock?.get("roll_number")?.toString()?.takeIf { it.isNotBlank() }
}

private fun resolveRollLength(info: Map<String, Any?>?): Double? {
    val stock = info?.get("stock") as? Map<*, *>
    return stock?.get("roll_length")?.toString()?.toDoubleOrNull()
}

@Suppress("UNCHECKED_CAST")
private fun resolveProductName(info: Map<String, Any?>?): String? =
    (info?.get("product") as? Map<*, *>)?.get("name") as? String

@Suppress("UNCHECKED_CAST")
private fun resolveProductSku(info: Map<String, Any?>?): String? =
    (info?.get("product") as? Map<*, *>)?.get("sku") as? String

@Suppress("UNCHECKED_CAST")
private fun resolveVariationLabel(info: Map<String, Any?>?): String? {
    val descriptors = info?.get("variation_descriptors") as? List<*>
    val fromDescriptors = descriptors?.mapNotNull { d ->
        (d as? Map<*, *>)?.get("label") as? String
    }?.joinToString(" · ")
    if (!fromDescriptors.isNullOrBlank()) return fromDescriptors
    val variation = info?.get("variation") as? Map<*, *>
    return (variation?.get("name") as? String)?.takeIf { it.isNotBlank() }
        ?: (variation?.get("value") as? String)?.takeIf { it.isNotBlank() }
}

@Suppress("UNCHECKED_CAST")
private fun resolveRollLabel(info: Map<String, Any?>?): String? {
    resolveRollNumber(info)?.let { return it }
    val rollLength = resolveRollLength(info)
    return rollLength?.takeIf { it > 0.0 }?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
}

private fun resolveQuantityUnitSuffix(info: Map<String, Any?>?): String? =
    info?.get("quantity_unit_suffix")?.toString()?.takeIf { it.isNotBlank() }

private fun resolveIsRoll(info: Map<String, Any?>?): Boolean {
    if (info?.get("product_type")?.toString() == "roll") return true
    return !resolveRollLabel(info).isNullOrBlank()
}

fun scanResolveEntryFromTagInfo(obj: JsonObject): ScanResolveEntry {
    if (obj.get("product_id")?.isJsonNull != false) {
        return ScanResolveEntry(ScanResolveStatus.UNKNOWN)
    }
    @Suppress("UNCHECKED_CAST")
    val map: Map<String, Any?> = Gson().fromJson(
        obj,
        object : TypeToken<Map<String, Any?>>() {}.type,
    )
    return entryFromResolveInfo(map)
}

fun scanResolveLabel(context: Context, entry: ScanResolveEntry?): String = when (entry?.status) {
    ScanResolveStatus.PENDING -> context.getString(R.string.symbol_ellipsis)
    ScanResolveStatus.UNKNOWN -> context.getString(R.string.scan_status_not_registered)
    ScanResolveStatus.REGISTERED -> buildString {
        entry.productName?.let { append(it) }
        entry.variationLabel?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
        entry.rollLabel?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(context.getString(R.string.scan_resolve_roll_prefix, it))
        }
        if (entry.isRoll && entry.rollLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" · ")
            append(context.getString(R.string.scan_resolve_roll_marker))
        }
    }.ifBlank { context.getString(R.string.scan_status_linked) }
    null -> context.getString(R.string.symbol_ellipsis)
}

private const val RESOLVE_CHUNK_SIZE = 100

/** Call after successful tag registration to refresh one code immediately. */
suspend fun refreshScanResolve(
    api: ErpApiService,
    code: String,
    onResult: (ScanResolveEntry) -> Unit,
) {
    val normalized = code.trim().uppercase()
    val res = api.resolve(com.erpcomplete.rfid.data.remote.ResolveRequest(normalized))
    if (!res.isSuccessful) {
        onResult(ScanResolveEntry(ScanResolveStatus.UNKNOWN))
        return
    }
    val row = res.body()?.data
    if (row?.resolved == true) {
        onResult(entryFromResolveInfo(row.info))
    } else {
        onResult(ScanResolveEntry(ScanResolveStatus.UNKNOWN))
    }
}

package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.data.model.BusinessUnitSummary
import com.erpcomplete.rfid.data.model.WorkspaceOption
import com.erpcomplete.rfid.data.model.WorkspacesPayload
import com.erpcomplete.rfid.data.remote.ApiEnvelope
import com.erpcomplete.rfid.ui.components.WorkflowListPage
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response

object WorkflowJson {
    private val gson = Gson()

    fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    fun JsonObject.long(key: String): Long? = when {
        !has(key) || get(key).isJsonNull -> null
        get(key).isJsonPrimitive && get(key).asJsonPrimitive.isNumber -> get(key).asLong
        else -> get(key).asString.toLongOrNull()
    }

    fun JsonObject.double(key: String): Double? = when {
        !has(key) || get(key).isJsonNull -> null
        get(key).isJsonPrimitive && get(key).asJsonPrimitive.isNumber -> get(key).asDouble
        else -> get(key).asString.toDoubleOrNull()
    }

    fun JsonObject.int(key: String): Int? = double(key)?.toInt()

    fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    fun JsonObject.productName(): String {
        obj("product")?.string("name")?.let { return it }
        return string("product_name") ?: "—"
    }

    fun JsonObject.productSku(): String {
        obj("product")?.string("sku")?.let { return it }
        return string("product_sku") ?: ""
    }

    fun JsonObject.variationLabel(): String {
        obj("variation_value")?.let { vv ->
            vv.string("display_label")?.takeIf { it.isNotBlank() }?.let { return it }
            vv.string("variation_name")?.takeIf { it.isNotBlank() }?.let { return it }
            vv.string("value")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        obj("variation")?.string("value")?.takeIf { it.isNotBlank() }?.let { return it }
        string("variation_value")?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    fun JsonObject.isRollStockLine(): Boolean {
        if (boolean("is_roll_product") == true) return true
        if (!string("roll_number").isNullOrBlank()) return true
        if ((double("roll_length") ?: 0.0) > 0.0) return true
        if (boolean("is_roll_quantity") == true) return true
        val productType = obj("product")?.obj("product_type")?.string("type")
            ?: obj("product")?.obj("productType")?.string("type")
        return productType == "roll"
    }

    /** Current on-hand amount at this stock row (roll_length for rolls, quantity otherwise). */
    fun JsonObject.onHandQuantity(): Double {
        if (!isRollStockLine()) return double("quantity") ?: 0.0
        val rollLength = double("roll_length") ?: 0.0
        val quantity = double("quantity") ?: 0.0
        return if (rollLength > 0.0) rollLength else quantity
    }

    /** Original / nominal roll length stored in quantity (rolls only). */
    fun JsonObject.rollNominalQuantity(): Double? =
        if (isRollStockLine()) double("quantity")?.takeIf { it > 0.0 } else null

    /** full when quantity equals roll_length; partial otherwise (rolls only). */
    fun JsonObject.rollFillStatus(): String? {
        string("roll_fill_status")?.takeIf { it.isNotBlank() }?.let { return it }
        if (!isRollStockLine()) return null
        val nominal = double("quantity") ?: 0.0
        if (nominal <= 0.0) return "partial"
        val onHand = double("roll_length") ?: onHandQuantity()
        return if (kotlin.math.abs(nominal - onHand) < 0.0001) "full" else "partial"
    }

    fun JsonObject.boolean(key: String): Boolean? =
        if (!has(key) || get(key).isJsonNull) null else get(key).asBoolean

    fun JsonObject.rollDisplayLabel(): String {
        string("roll_number")?.takeIf { it.isNotBlank() }?.let { return it }
        double("roll_length")?.takeIf { it > 0.0 }?.let { return DisplayFormat.qty(it) }
        return ""
    }

    fun JsonObject.quantityUnitSuffix(): String? {
        string("quantity_unit_suffix")?.takeIf { it.isNotBlank() }?.let { return it }
        if (!isRollStockLine()) return null
        obj("product")?.obj("product_unit")?.string("code")?.takeIf { it.isNotBlank() }?.let { return it }
        obj("product")?.string("unit_of_measure")?.takeIf { it.isNotBlank() }?.let { return it }
        string("unit_of_measure")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    fun formatQtyWithUnit(qty: String, unit: String?, isRoll: Boolean = false): String {
        val suffix = unit?.takeIf { it.isNotBlank() }
        return if (suffix != null) "$qty $suffix" else qty
    }

    fun rollLengthLabel(unit: String?, prefix: String = "Length"): String {
        val suffix = unit?.takeIf { it.isNotBlank() }
        return if (suffix != null) "$prefix ($suffix)" else prefix
    }

    fun JsonObject.lineDetailLabel(): String = buildString {
        append(productName())
        variationLabel().takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        rollDisplayLabel().takeIf { it.isNotBlank() }?.let { append(" · roll ").append(it) }
        if (isRollStockLine() && rollDisplayLabel().isBlank()) append(" (roll)")
    }

    fun envelopeObject(response: Response<ApiEnvelope<JsonElement>>): JsonObject? {
        val data = response.body()?.data ?: return null
        return data.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** Unwrap `{ pick_list: … }` responses after complete-picking. */
    fun unwrapPickList(data: JsonObject?): JsonObject? = data?.obj("pick_list") ?: data

    fun envelopeList(response: Response<ApiEnvelope<JsonElement>>): List<JsonObject> {
        val data = response.body()?.data ?: return emptyList()
        if (!data.isJsonArray) return emptyList()
        return data.asJsonArray.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject }
    }

    fun envelopePage(response: Response<ApiEnvelope<JsonElement>>, page: Int): WorkflowListPage {
        val body = response.body()
        val pagination = body?.pagination
        return WorkflowListPage(
            rows = envelopeList(response),
            page = pagination?.current_page ?: page,
            lastPage = pagination?.last_page ?: page,
            total = pagination?.total,
        )
    }

    fun parseWorkspaceRow(row: JsonObject): WorkspaceOption? {
        val warehouseId = row.long("warehouse_id") ?: return null
        val businessUnitId = row.long("business_unit_id") ?: return null
        return WorkspaceOption(
            warehouseId = warehouseId,
            warehouseName = row.string("warehouse_name") ?: "Warehouse #$warehouseId",
            warehouseCode = row.string("warehouse_code"),
            businessUnitId = businessUnitId,
            businessUnitName = row.string("business_unit_name") ?: "Business unit",
            teamId = row.long("team_id"),
            teamName = row.string("team_name"),
        )
    }

    fun envelopeWorkspaces(response: Response<ApiEnvelope<JsonElement>>): List<WorkspaceOption> =
        envelopeWorkspacesPayload(response).warehouses

    fun envelopeWorkspacesPayload(response: Response<ApiEnvelope<JsonElement>>): WorkspacesPayload {
        val data = response.body()?.data ?: return WorkspacesPayload(emptyList(), emptyList())
        if (data.isJsonObject) {
            val root = data.asJsonObject
            val businessUnits = root.array("business_units")?.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.let { bu ->
                    val id = bu.long("id") ?: return@mapNotNull null
                    BusinessUnitSummary(
                        id = id,
                        name = bu.string("name") ?: "Business unit",
                        warehouseCount = bu.int("warehouse_count") ?: 0,
                    )
                }
            } ?: emptyList()
            val warehouses = root.array("warehouses")?.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseWorkspaceRow)
            } ?: emptyList()
            return WorkspacesPayload(businessUnits, warehouses)
        }
        if (data.isJsonArray) {
            val warehouses = data.asJsonArray.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseWorkspaceRow)
            }
            val businessUnits = warehouses
                .groupBy { it.businessUnitId }
                .map { (id, list) ->
                    BusinessUnitSummary(
                        id = id,
                        name = list.first().businessUnitName,
                        warehouseCount = list.size,
                    )
                }
                .sortedBy { it.name.lowercase() }
            return WorkspacesPayload(businessUnits, warehouses)
        }
        return WorkspacesPayload(emptyList(), emptyList())
    }

    fun envelopeTotal(response: Response<ApiEnvelope<JsonElement>>): Int? =
        response.body()?.pagination?.total

    fun envelopeCount(response: Response<ApiEnvelope<JsonElement>>): Int =
        envelopeTotal(response) ?: envelopeList(response).size

    fun parseEnvelope(raw: String?): JsonObject? =
        runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()

    fun nestedItems(gr: JsonObject, key: String = "items"): List<JsonObject> =
        gr.array(key)?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject } ?: emptyList()

    fun locationLabel(loc: JsonObject?): String {
        if (loc == null) return ""
        loc.string("location_code")?.let { return it }
        val parts = listOfNotNull(
            loc.string("zone_code"),
            loc.string("aisle"),
            loc.string("rack"),
            loc.string("shelf"),
            loc.string("bin"),
        ).filter { it.isNotBlank() }
        return parts.joinToString("-").ifBlank { loc.long("id")?.toString() ?: "" }
    }
}

package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.google.gson.JsonObject

object PickerMappers {

    fun product(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val name = row.string("name") ?: "Product #$id"
        val sku = row.string("sku")
        return PickerOption(id = id, title = name, subtitle = sku)
    }

    fun purchaseOrder(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val number = row.string("purchase_order_number") ?: "PO #$id"
        val supplierObj = row.obj("supplier")
        val supplier = supplierObj?.string("company")
            ?: supplierObj?.string("contact_person")
        val state = row.string("state")
        return PickerOption(
            id = id,
            title = number,
            subtitle = listOfNotNull(supplier, state?.let { DisplayFormat.status(it) }).joinToString(" · "),
        )
    }

    fun warehouse(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val name = row.string("name") ?: "Warehouse #$id"
        val code = row.string("code")
        return PickerOption(id = id, title = name, subtitle = code)
    }

    fun warehouseLocation(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val label = WorkflowJson.locationLabel(row).ifBlank { "Location #$id" }
        return PickerOption(id = id, title = label, subtitle = row.string("zone_code"))
    }

    fun user(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val name = row.string("name") ?: "User #$id"
        val email = row.string("email")
        return PickerOption(id = id, title = name, subtitle = email)
    }

    fun stockTransfer(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val number = row.string("stock_transfer_number") ?: "ST #$id"
        val from = row.obj("from_warehouse")?.string("name") ?: row.obj("fromWarehouse")?.string("name")
        val to = row.obj("to_warehouse")?.string("name") ?: row.obj("toWarehouse")?.string("name")
        return PickerOption(
            id = id,
            title = number,
            subtitle = listOfNotNull(from, to).joinToString(" → "),
        )
    }

    fun salesReturn(row: JsonObject): PickerOption? {
        val id = row.long("id") ?: return null
        val number = row.string("return_number") ?: "SR #$id"
        val contact = row.obj("contact")?.string("company")
        return PickerOption(id = id, title = number, subtitle = contact)
    }
}

package com.erpcomplete.rfid.data.model

import com.google.gson.JsonObject

data class PageActionPermissions(
    val read: Boolean = false,
    val create: Boolean = false,
    val update: Boolean = false,
    val delete: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JsonObject?): PageActionPermissions {
            if (obj == null) return PageActionPermissions()
            return PageActionPermissions(
                read = obj.get("read")?.asBoolean == true,
                create = obj.get("create")?.asBoolean == true,
                update = obj.get("update")?.asBoolean == true,
                delete = obj.get("delete")?.asBoolean == true,
            )
        }
    }
}

data class MobileInventoryPermissions(
    val inventoryRead: Boolean = false,
    val stockAdjustment: PageActionPermissions = PageActionPermissions(),
    val stockRelocation: PageActionPermissions = PageActionPermissions(),
) {
    companion object {
        val None = MobileInventoryPermissions()

        fun fromJson(root: JsonObject?): MobileInventoryPermissions {
            if (root == null) return None
            val inventory = root.getAsJsonObject("inventory")
            return MobileInventoryPermissions(
                inventoryRead = inventory?.get("read")?.asBoolean == true,
                stockAdjustment = PageActionPermissions.fromJson(root.getAsJsonObject("stock_adjustment")),
                stockRelocation = PageActionPermissions.fromJson(root.getAsJsonObject("stock_relocation")),
            )
        }
    }
}

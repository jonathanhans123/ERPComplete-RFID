package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.data.remote.ApiEnvelope
import com.erpcomplete.rfid.data.remote.PaginationMeta
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class WorkflowJsonTest {

    @Test
    fun envelopePage_usesPaginationMeta() {
        val rows: JsonElement = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", 1) })
            add(JsonObject().apply { addProperty("id", 2) })
        }
        val envelope = ApiEnvelope<JsonElement>(
            success = true,
            data = rows,
            pagination = PaginationMeta(current_page = 2, last_page = 5, total = 120),
        )
        val response = Response.success(envelope)
        val page = WorkflowJson.envelopePage(response, 2)
        assertEquals(2, page.rows.size)
        assertEquals(2, page.page)
        assertEquals(5, page.lastPage)
        assertEquals(120, page.total)
    }
}

package com.erpcomplete.rfid.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The ERP validates `rejection_reason` as required on reject and `approval_notes` as optional on approve. */
class ApprovalDecisionRequestTest {
    private val gson = Gson()

    @Test
    fun rejectBodySendsOnlyReason() {
        val json = JsonParser.parseString(gson.toJson(ApprovalDecisionRequest(rejection_reason = "Wrong count"))).asJsonObject
        assertEquals("Wrong count", json.get("rejection_reason").asString)
        assertFalse(json.has("approval_notes"))
    }

    @Test
    fun approveWithoutNotesSendsEmptyObject() {
        assertEquals("{}", gson.toJson(ApprovalDecisionRequest()))
    }
}

package com.erpcomplete.rfid.ui.components

import com.google.gson.JsonObject

data class WorkflowListPage(
    val rows: List<JsonObject>,
    val page: Int = 1,
    val lastPage: Int = 1,
    val total: Int? = null,
)

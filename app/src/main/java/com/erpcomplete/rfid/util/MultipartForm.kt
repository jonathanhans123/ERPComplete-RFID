package com.erpcomplete.rfid.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Turns a request object into multipart text fields using PHP bracket notation
 * (`items[0][product_id]`), the way a browser form posts nested data, so the same Laravel validation applies
 * to a JSON body and to a body that also carries a file. Nulls are left out; booleans become 1/0.
 */
object MultipartForm {
    private val textType = "text/plain; charset=utf-8".toMediaType()

    fun fields(body: Any, gson: Gson = Gson()): Map<String, RequestBody> {
        val out = linkedMapOf<String, RequestBody>()
        flatten("", gson.toJsonTree(body), out)
        return out
    }

    private fun flatten(prefix: String, element: JsonElement, out: MutableMap<String, RequestBody>) {
        when (element) {
            is JsonNull -> Unit
            is JsonObject -> element.entrySet().forEach { (key, value) ->
                flatten(if (prefix.isEmpty()) key else "$prefix[$key]", value, out)
            }
            is JsonArray -> element.forEachIndexed { index, value -> flatten("$prefix[$index]", value, out) }
            else -> {
                val primitive = element.asJsonPrimitive
                val text = if (primitive.isBoolean) (if (primitive.asBoolean) "1" else "0") else primitive.asString
                out[prefix] = text.toRequestBody(textType)
            }
        }
    }
}

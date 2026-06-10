package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorParser {

    private val gson = Gson()

    fun networkMessage(throwable: Throwable): String = when (throwable) {
        is UnknownHostException -> "Cannot find server. Check Wi‑Fi and API URL in Settings."
        is ConnectException -> "Cannot connect to server. Is Laravel running?"
        is SocketTimeoutException -> "Connection timed out. Check network and firewall."
        is IOException -> throwable.message?.takeIf { it.isNotBlank() }
            ?: "Network error — connection failed."
        else -> throwable.message ?: "Unexpected error"
    }

    fun httpMessage(response: Response<*>, authenticated: Boolean = false): String = when (response.code()) {
        401 -> if (authenticated) {
            parseBody(response) ?: "Session expired. Sign in again."
        } else {
            parseBody(response) ?: "Email or password is incorrect."
        }
        422 -> parseBody(response) ?: "Please check your input."
        429 -> "Too many attempts. Wait about a minute, then try again."
        in 500..599 -> "Server error (${response.code()}). Try again later."
        else -> parseBody(response) ?: "Request failed (${response.code()})."
    }

    private fun parseBody(response: Response<*>): String? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
            ?: runCatching { response.body()?.let { gson.toJson(it) } }.getOrNull()
        if (raw.isNullOrBlank()) return null
        if (raw.contains("<html", ignoreCase = true)) {
            return "Reached a web page instead of the API. Check API URL in Settings."
        }
        return runCatching {
            val json = gson.fromJson(raw, JsonObject::class.java)
            json.get("message")?.takeIf { !it.isJsonNull }?.asString
                ?: json.getAsJsonObject("errors")?.entrySet()?.firstOrNull()?.value?.asJsonArray?.firstOrNull()?.asString
        }.getOrNull() ?: raw.take(160)
    }
}

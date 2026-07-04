package com.erpcomplete.rfid.util

import android.content.Context
import com.erpcomplete.rfid.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorParser {

    private val gson = Gson()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun ctx(): Context = appContext

    fun genericError(): String = ctx().getString(R.string.error_something_wrong)

    fun networkMessage(throwable: Throwable): String = when (throwable) {
        is UnknownHostException -> ctx().getString(R.string.error_network_unknown_host)
        is ConnectException -> ctx().getString(R.string.error_network_connect)
        is SocketTimeoutException -> ctx().getString(R.string.error_network_timeout)
        is IOException -> throwable.message?.takeIf { it.isNotBlank() }
            ?: ctx().getString(R.string.error_network_generic)
        else -> throwable.message ?: ctx().getString(R.string.error_unexpected)
    }

    fun httpMessage(response: Response<*>, authenticated: Boolean = false): String = when (response.code()) {
        401 -> if (authenticated) {
            parseBody(response) ?: ctx().getString(R.string.error_session_expired)
        } else {
            parseBody(response) ?: ctx().getString(R.string.error_invalid_credentials)
        }
        422 -> parseBody(response) ?: ctx().getString(R.string.error_check_input)
        429 -> ctx().getString(R.string.error_too_many_attempts)
        in 500..599 -> ctx().getString(R.string.error_server, response.code())
        403 -> parseBody(response) ?: ctx().getString(R.string.error_request_failed, response.code())
        else -> parseBody(response) ?: ctx().getString(R.string.error_request_failed, response.code())
    }

    fun isTwoFactorRequired(response: Response<*>): Boolean {
        if (response.code() != 403) return false
        val raw = runCatching { response.errorBody()?.string() }.getOrNull() ?: return false
        return runCatching {
            gson.fromJson(raw, JsonObject::class.java).get("two_factor_required")?.asBoolean == true
        }.getOrNull() == true
    }

    private fun parseBody(response: Response<*>): String? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
            ?: runCatching { response.body()?.let { gson.toJson(it) } }.getOrNull()
        if (raw.isNullOrBlank()) return null
        if (raw.contains("<html", ignoreCase = true)) {
            return ctx().getString(R.string.error_web_page_not_api)
        }
        return runCatching {
            val json = gson.fromJson(raw, JsonObject::class.java)
            json.get("message")?.takeIf { !it.isJsonNull }?.asString
                ?: json.getAsJsonObject("errors")?.entrySet()?.firstOrNull()?.value?.asJsonArray?.firstOrNull()?.asString
        }.getOrNull() ?: raw.take(160)
    }
}

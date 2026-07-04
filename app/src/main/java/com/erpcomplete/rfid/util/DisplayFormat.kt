package com.erpcomplete.rfid.util

import android.content.Context
import androidx.annotation.StringRes
import com.erpcomplete.rfid.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DisplayFormat {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun status(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return raw
            .trim()
            .replace('_', ' ')
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
    }

    fun status(context: Context, raw: String?): String {
        if (raw.isNullOrBlank()) return context.getString(R.string.symbol_em_dash)
        val key = raw.trim().lowercase(Locale.getDefault()).replace(' ', '_')
        @StringRes val res = when (key) {
            "active" -> R.string.status_active
            "inactive" -> R.string.status_inactive
            "pending" -> R.string.api_status_pending
            "draft" -> R.string.api_status_draft
            "in_progress" -> R.string.api_status_in_progress
            "completed", "completed_picking" -> R.string.api_status_completed
            "approved" -> R.string.api_status_approved
            "cancelled", "canceled" -> R.string.api_status_cancelled
            "rejected" -> R.string.api_status_rejected
            "partial" -> R.string.api_status_partial
            "full" -> R.string.roll_status_full
            "done" -> R.string.api_status_done
            "failed", "fail" -> R.string.api_status_failed
            "purchase_order" -> R.string.gr_source_type_po_title
            "stock_transfer" -> R.string.gr_source_type_transfer_title
            "sales_return" -> R.string.gr_source_type_return_title
            "cycle_count" -> R.string.cycle_count_title
            "full_count", "full" -> R.string.opname_type_full
            "in_transit" -> R.string.api_status_in_transit
            "received" -> R.string.api_status_received
            "simple" -> R.string.api_type_simple
            else -> return status(raw)
        }
        return context.getString(res)
    }

    fun date(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        parseInstant(raw)?.let { return dateFormatter.format(it.atZone(ZoneId.systemDefault())) }
        return runCatching { LocalDate.parse(raw.take(10)).format(dateFormatter) }
            .getOrElse { raw.take(10) }
    }

    fun dateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        parseInstant(raw)?.let { return dateTimeFormatter.format(it.atZone(ZoneId.systemDefault())) }
        return runCatching {
            LocalDateTime.parse(raw.replace("Z", "").take(19)).format(dateTimeFormatter)
        }.getOrElse { date(raw) }
    }

    fun timeAgo(context: Context, epochMs: Long?): String {
        if (epochMs == null) return ""
        val seconds = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 10 -> context.getString(R.string.time_ago_just_now)
            seconds < 60 -> context.getString(R.string.time_ago_seconds, seconds)
            seconds < 3600 -> context.getString(R.string.time_ago_minutes, seconds / 60)
            else -> timeFormatter.format(
                Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
            )
        }
    }

    fun qty(value: Double?): String {
        if (value == null) return "—"
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    private fun parseInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

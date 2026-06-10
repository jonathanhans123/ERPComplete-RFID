package com.erpcomplete.rfid.util

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

    fun timeAgo(epochMs: Long?): String {
        if (epochMs == null) return ""
        val seconds = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 10 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
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

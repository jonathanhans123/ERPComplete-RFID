package com.erpcomplete.rfid.rfid

object ZebraFirmwareVersion {

    private val tokenPattern = Regex("""(SAAF[A-Z0-9]+-\d+-R\d+[A-Z0-9]*)""", RegexOption.IGNORE_CASE)

    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.uppercase()?.replace(Regex("""\.DAT$"""), "")?.takeIf { it.isNotBlank() }
            ?: return null
        return tokenPattern.find(value)?.groupValues?.get(1)?.uppercase() ?: value
    }

    fun compare(a: String?, b: String?): Int {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == null || nb == null) return (na ?: "").compareTo(nb ?: "")
        val ta = parseTuple(na)
        val tb = parseTuple(nb)
        if (ta == null || tb == null) return na.compareTo(nb)
        return when {
            ta.first != tb.first -> ta.first.compareTo(tb.first)
            ta.second != tb.second -> ta.second.compareTo(tb.second)
            else -> ta.third.compareTo(tb.third)
        }
    }

    fun isNewer(candidate: String?, current: String?): Boolean =
        compare(candidate, current) > 0

    private fun parseTuple(version: String): Triple<Int, Int, String>? {
        val match = Regex("""^[A-Z0-9]+-(\d+)-R(\d+)([A-Z0-9]*)$""").matchEntire(version) ?: return null
        return Triple(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3],
        )
    }

    fun resolveReaderModel(modelName: String?, readerName: String?): String? {
        val host = readerName?.uppercase().orEmpty()
        val model = modelName?.uppercase().orEmpty()
        return when {
            host.startsWith("RFD90") || model.startsWith("RFD90") -> "RFD90"
            host.startsWith("RFD40") || model.startsWith("RFD40") -> "RFD40"
            host.startsWith("RFD8500") || model.startsWith("RFD8500") -> "RFD8500"
            else -> null
        }
    }
}

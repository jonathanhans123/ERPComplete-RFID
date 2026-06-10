package com.erpcomplete.rfid.ui.components

data class WorkflowMatchLine(
    val key: String,
    val productId: Long,
    val variationValueId: Long?,
    val rollNumber: String?,
    val isRoll: Boolean = false,
    val label: String = "",
)

enum class ScanMatchStatus {
    PENDING,
    UNKNOWN,
    UNMATCHED,
    MATCHED,
    FORCED,
}

/** Piece goods: +1 per scan. Roll goods: length is entered manually — no scan qty bump. */
fun shouldIncrementQtyOnScan(line: WorkflowMatchLine, entry: ScanResolveEntry): Boolean =
    !line.isRoll && !entry.isRoll

fun scanIncrementDelta(entry: ScanResolveEntry): Double = if (entry.isRoll) 0.0 else 1.0

object WorkflowLineMatcher {
    fun findMatchingLine(
        entry: ScanResolveEntry,
        lines: List<WorkflowMatchLine>,
        forcedLineKey: String? = null,
    ): WorkflowMatchLine? {
        if (entry.status != ScanResolveStatus.REGISTERED) return null
        if (!forcedLineKey.isNullOrBlank()) {
            return lines.firstOrNull { it.key == forcedLineKey }
        }
        val productId = entry.productId ?: return null
        val variationId = entry.variationValueId
        val roll = entry.rollNumber?.trim()?.takeIf { it.isNotBlank() }

        if (roll != null) {
            lines.firstOrNull {
                it.productId == productId &&
                    it.rollNumber != null &&
                    it.rollNumber.equals(roll, ignoreCase = true)
            }?.let { return it }
        }

        if (variationId != null) {
            val variationMatches = lines.filter {
                it.productId == productId && it.variationValueId == variationId
            }
            if (roll != null) {
                variationMatches.firstOrNull {
                    it.rollNumber?.equals(roll, ignoreCase = true) == true
                }?.let { return it }
            }
            val rollOnly = variationMatches.filter { it.isRoll }
            if (rollOnly.isNotEmpty()) return null
            if (variationMatches.size == 1) return variationMatches.first()
            variationMatches.firstOrNull { it.rollNumber.isNullOrBlank() && !it.isRoll }?.let { return it }
        }

        val productMatches = lines.filter { it.productId == productId }
        val rollLines = productMatches.filter { it.isRoll }
        if (rollLines.isNotEmpty()) {
            if (roll != null) {
                rollLines.firstOrNull { it.rollNumber?.equals(roll, ignoreCase = true) == true }?.let { return it }
            }
            return null
        }
        return when (productMatches.size) {
            1 -> productMatches.first()
            else -> productMatches.firstOrNull {
                it.variationValueId == null && it.rollNumber.isNullOrBlank()
            }
        }
    }

    fun matchStatus(
        entry: ScanResolveEntry?,
        lines: List<WorkflowMatchLine>,
        forcedLineKey: String? = null,
    ): ScanMatchStatus {
        if (entry == null || entry.status == ScanResolveStatus.PENDING) return ScanMatchStatus.PENDING
        if (entry.status == ScanResolveStatus.UNKNOWN) return ScanMatchStatus.UNKNOWN
        val matched = findMatchingLine(entry, lines, forcedLineKey)
        return when {
            matched != null && !forcedLineKey.isNullOrBlank() -> ScanMatchStatus.FORCED
            matched != null -> ScanMatchStatus.MATCHED
            else -> ScanMatchStatus.UNMATCHED
        }
    }
}

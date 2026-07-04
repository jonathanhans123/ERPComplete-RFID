package com.erpcomplete.rfid.rfid

import android.content.Context
import android.util.Log
import com.erpcomplete.rfid.R
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION
import com.zebra.rfid.api3.FILTER_ACTION
import com.zebra.rfid.api3.MEMORY_BANK
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.PreFilters
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.STATE_AWARE_ACTION
import com.zebra.rfid.api3.TARGET
import com.zebra.rfid.api3.TagAccess
import com.zebra.rfid.api3.TagData

internal object RfidHardwareOps {

    private const val TAG = "RfidHardwareOps"
    private const val EPC_FILTER_BIT_OFFSET = 32

    fun normalizeHexPattern(raw: String?): String? {
        val hex = raw?.trim()?.uppercase()?.replace(Regex("[^0-9A-F]"), "")?.takeIf { it.isNotBlank() }
            ?: return null
        if (hex.length % 2 != 0) return null
        return hex
    }

    fun epcPrefixPattern(fullEpc: String, maxHexChars: Int = 12): String? {
        val hex = normalizeHexPattern(fullEpc) ?: return null
        return hex.take(maxHexChars.coerceAtMost(hex.length)).takeIf { it.length >= 4 }
    }

    fun applyEpcPrefilter(r: RFIDReader, patternHex: String?) {
        val pattern = normalizeHexPattern(patternHex)
        runCatching { r.Actions.PreFilters.deleteAll() }
        if (pattern == null) return
        val bitCount = pattern.length * 4
        val preFilters = PreFilters()
        val preFilter = preFilters.PreFilter()
        preFilter.setAntennaID(1.toShort())
        preFilter.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
        preFilter.setBitOffset(EPC_FILTER_BIT_OFFSET)
        preFilter.setTagPattern(pattern)
        preFilter.setTagPatternBitCount(bitCount)
        preFilter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
        preFilter.StateAwareAction.setTarget(TARGET.TARGET_SL)
        preFilter.StateAwareAction.setStateAwareAction(
            STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_B_NOT_INV_A,
        )
        r.Actions.PreFilters.add(arrayOf(preFilter), null)
        Log.i(TAG, "EPC pre-filter applied: $pattern ($bitCount bits @ offset $EPC_FILTER_BIT_OFFSET)")
    }

    fun clearEpcPrefilter(r: RFIDReader) {
        r.Actions.PreFilters.deleteAll()
    }

    fun writeEpc(context: Context, r: RFIDReader, sourceTagEpc: String, newEpcHex: String): TagWriteResult {
        val source = normalizeHexPattern(sourceTagEpc)
            ?: return TagWriteResult.Failure(context.getString(R.string.rfid_epc_invalid_source))
        val target = normalizeHexPattern(newEpcHex)
            ?: return TagWriteResult.Failure(context.getString(R.string.rfid_epc_invalid_target))
        if (target.length % 4 != 0) {
            return TagWriteResult.Failure(context.getString(R.string.rfid_epc_invalid_length))
        }

        return try {
            runCatching { r.Actions.Inventory.stop() }
            r.Config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE)

            val tagAccess = TagAccess()
            val writeParams = tagAccess.WriteAccessParams()
            writeParams.setAccessPassword(0)
            writeParams.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
            writeParams.setOffset(2)
            writeParams.setWriteData(target)
            writeParams.setWriteDataLength(target.length / 4)

            val sourceTag = TagData()
            sourceTag.setTagID(source)
            r.Actions.TagAccess.writeWait(source, writeParams, null, sourceTag, true, true)
            TagWriteResult.Success(target)
        } catch (e: OperationFailureException) {
            TagWriteResult.Failure(
                e.vendorMessage ?: e.message ?: context.getString(R.string.rfid_epc_write_failed),
            )
        } catch (e: Exception) {
            TagWriteResult.Failure(e.message ?: context.getString(R.string.rfid_epc_write_failed))
        }
    }

    fun readFirmwareVersion(r: RFIDReader): String? = try {
        r.ReaderCapabilities.getFirwareVersion()
    } catch (_: Exception) {
        null
    }

    fun readModelName(r: RFIDReader): String? = try {
        r.ReaderCapabilities.getModelName()
    } catch (_: Exception) {
        null
    }

    fun readSerialNumber(r: RFIDReader): String? = try {
        r.ReaderCapabilities.getSerialNumber()
    } catch (_: Exception) {
        null
    }

    data class BatteryReading(
        val percent: Int?,
        val charging: Boolean?,
    )

    /** Triggers a BATTERY_EVENT on the SDK event listener (required on RFD40/90). */
    fun pollBatteryStatus(r: RFIDReader) {
        runCatching { r.Config.getDeviceStatus(true, false, false) }
    }

    fun readBatteryStatsSync(r: RFIDReader): BatteryReading = runCatching {
        val stats = r.Config.getBatteryStats()
        val percent = runCatching { stats.getPercentage() }.getOrNull()?.takeIf { it in 0..100 }
        val charging = chargingFromRaw(runCatching { stats.getCharging() }.getOrNull())
        BatteryReading(percent, charging)
    }.getOrElse { BatteryReading(null, null) }

    fun readBattery(r: RFIDReader): BatteryReading {
        pollBatteryStatus(r)
        return readBatteryStatsSync(r)
    }

    fun batteryFromEvent(level: Int?, chargingRaw: Any?): BatteryReading {
        val percent = level?.takeIf { it in 0..100 }
        return BatteryReading(percent, chargingFromRaw(chargingRaw))
    }

    private fun chargingFromRaw(raw: Any?): Boolean? = when (raw) {
        null -> null
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> null
    }

    fun resolveSerialNumber(r: RFIDReader, readerName: String?): String? {
        readSerialNumber(r)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        readerName?.let(::serialFromReaderLabel)?.let { return it }
        runCatching { r.hostName }?.getOrNull()?.let(::serialFromReaderLabel)?.let { return it }
        return null
    }

    fun serialFromReaderLabel(value: String): String? {
        val label = value.trim().uppercase()
        if (label.isBlank()) return null
        Regex("""S[/\s]*N[:\s#-]*([A-Z0-9]{6,20})""").find(label)?.groupValues?.get(1)?.let { return it }
        Regex("""RFD\d{2}[-_]?([A-Z0-9]{8,14})""").find(label)?.groupValues?.get(1)?.let { return it }
        Regex("""\b(\d{10,14})\b""").find(label)?.groupValues?.get(1)?.let { return it }
        return null
    }
}

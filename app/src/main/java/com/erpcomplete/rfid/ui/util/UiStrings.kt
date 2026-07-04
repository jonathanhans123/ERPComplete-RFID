package com.erpcomplete.rfid.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.util.DisplayFormat

object UiStrings {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun ctx(): Context = appContext

    fun businessUnitFallback(): String = ctx().getString(R.string.label_business_unit_fallback)

    fun warehouseFallbackName(warehouseId: Long): String =
        ctx().getString(R.string.warehouse_fallback_id, warehouseId)

    fun productFallback(productId: Long): String =
        ctx().getString(R.string.product_fallback_title, productId)

    fun locationFallback(locationId: Long): String =
        ctx().getString(R.string.putaway_fallback_location, locationId)

    fun userFallback(userId: Long): String =
        ctx().getString(R.string.gr_fallback_user_name, userId)

    fun poFallback(id: Long): String = ctx().getString(R.string.picker_po_fallback, id)

    fun stFallback(id: Long): String = ctx().getString(R.string.picker_st_fallback, id)

    fun srFallback(id: Long): String = ctx().getString(R.string.picker_sr_fallback, id)

    fun savedOfflineSync(): String = ctx().getString(R.string.error_saved_offline_sync)

    fun apiStatus(raw: String?): String = DisplayFormat.status(ctx(), raw)

    fun grSourceType(sourceType: String?): String = when (sourceType?.lowercase()) {
        "stock_transfer" -> ctx().getString(R.string.gr_source_type_transfer_title)
        "sales_return" -> ctx().getString(R.string.gr_source_type_return_title)
        "purchase_order", null, "" -> ctx().getString(R.string.gr_source_type_po_title)
        else -> apiStatus(sourceType)
    }

    fun scanProfileLabel(code: String?): String = when (code?.lowercase()) {
        "dense" -> ctx().getString(R.string.settings_scan_dense)
        "range" -> ctx().getString(R.string.settings_scan_range)
        else -> code?.takeIf { it.isNotBlank() } ?: emDash()
    }

    fun httpSyncError(): String = ctx().getString(R.string.error_http_sync)

    fun firmwarePhaseLabel(phase: String): String = firmwarePhase(ctx(), phase)

    fun emDash(): String = ctx().getString(R.string.symbol_em_dash)

    fun emDash(context: Context): String = context.getString(R.string.symbol_em_dash)

    fun locationType(context: Context, code: String?): String {
        if (code.isNullOrBlank()) return emDash(context)
        @StringRes val res = when (code.lowercase()) {
            "storage" -> R.string.location_type_storage
            "receiving" -> R.string.location_type_receiving
            "picking" -> R.string.location_type_picking
            "shipping" -> R.string.location_type_shipping
            "quality" -> R.string.location_type_quality
            else -> return code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        return context.getString(res)
    }

    fun adjustmentType(context: Context, code: String?): String {
        if (code.isNullOrBlank()) return emDash(context)
        @StringRes val res = when (code.lowercase()) {
            "correction" -> R.string.adjustment_type_correction
            "loss" -> R.string.adjustment_type_loss
            "damage" -> R.string.adjustment_type_damage
            "expiry" -> R.string.adjustment_type_expiry
            "theft" -> R.string.adjustment_type_theft
            "other" -> R.string.adjustment_type_other
            else -> return code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        return context.getString(res)
    }

    fun firmwarePhase(context: Context, phase: String): String {
        @StringRes val res = when (phase.uppercase()) {
            "IDLE" -> R.string.firmware_phase_idle
            "CHECKING" -> R.string.firmware_phase_checking
            "UP_TO_DATE" -> R.string.firmware_phase_up_to_date
            "UPDATE_AVAILABLE" -> R.string.firmware_phase_update_available
            "READY_TO_INSTALL" -> R.string.firmware_phase_ready_to_install
            "DOWNLOADING" -> R.string.firmware_phase_downloading
            "INSTALLING" -> R.string.firmware_phase_installing
            "SUCCESS" -> R.string.firmware_phase_success
            "FAILED" -> R.string.firmware_phase_failed
            else -> return phase.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
        return context.getString(res)
    }

    @Composable
    fun locationType(code: String?): String {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        return locationType(ctx, code)
    }

    @Composable
    fun adjustmentType(code: String?): String {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        return adjustmentType(ctx, code)
    }

    @Composable
    fun firmwarePhase(phase: String): String {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        return firmwarePhase(ctx, phase)
    }
}

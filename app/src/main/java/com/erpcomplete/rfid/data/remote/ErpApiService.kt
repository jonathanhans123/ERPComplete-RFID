package com.erpcomplete.rfid.data.remote

import com.erpcomplete.rfid.data.model.BusinessUnitOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ErpApiService {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Map<String, String>>

    @POST("auth/refresh")
    suspend fun refreshToken(): Response<RefreshTokenResponse>

    @GET("auth/user")
    suspend fun currentUser(): Response<CurrentUserResponse>

    @GET("auth/workspaces")
    suspend fun listWorkspaces(
        @Query("business_unit_id") businessUnitId: Long? = null,
    ): Response<ApiEnvelope<JsonElement>>

    @POST("rfid/resolve")
    suspend fun resolve(@Body body: ResolveRequest): Response<ApiEnvelope<ResolveResult>>

    @POST("rfid/resolve-bulk")
    suspend fun resolveBulk(@Body body: ResolveBulkRequest): Response<ApiEnvelope<ResolveBulkResult>>

    @POST("rfid/reads")
    suspend fun ingestReads(@Body body: ReadsRequest): Response<ApiEnvelope<ReadsResult>>

    @GET("rfid/tags")
    suspend fun listRfidTags(
        @Query("product_id") productId: Long,
        @Query("variation_value_id") variationValueId: Long? = null,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @POST("rfid/tags")
    suspend fun registerTag(@Body body: RegisterTagRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("rfid/tags/{epc}/void")
    suspend fun voidTag(@Path("epc") epc: String, @Body body: VoidTagRequest = VoidTagRequest()): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("rfid/encode/request")
    suspend fun requestEncode(@Body body: EncodeRequest): Response<ApiEnvelope<EncodeJobResult>>

    @POST("rfid/encode/confirm")
    suspend fun confirmEncode(@Body body: EncodeConfirmRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @GET("rfid/firmware/check")
    suspend fun checkFirmwareUpdate(
        @Query("model") model: String,
        @Query("current_version") currentVersion: String? = null,
        @Query("refresh") refresh: Int = 0,
    ): Response<ApiEnvelope<FirmwareCheckResult>>

    @POST("rfid/sessions")
    suspend fun startSession(@Body body: StartSessionRequest): Response<ApiEnvelope<SessionWrapper>>

    @POST("rfid/sessions/{id}/submit")
    suspend fun submitSession(@Path("id") id: String, @Body body: SubmitSessionRequest): Response<ApiEnvelope<SubmitSessionResult>>

    @POST("goods-receipts/{id}/rfid-scan")
    suspend fun goodsReceiptRfidScan(@Path("id") id: Long, @Body body: WorkflowScanRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("putaway-tasks/{id}/rfid-confirm")
    suspend fun putawayRfidConfirm(@Path("id") id: Long, @Body body: PutawayConfirmRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("inventory-pick-lists/{id}/rfid-pick-confirm")
    suspend fun pickRfidConfirm(@Path("id") id: Long, @Body body: WorkflowScanRequest): Response<ApiEnvelope<JsonElement>>

    @GET("goods-receipts")
    suspend fun listGoodsReceipts(
        @Query("status") status: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("goods-receipts/{id}")
    suspend fun getGoodsReceipt(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("goods-receipts")
    suspend fun createGoodsReceipt(@Body body: CreateGoodsReceiptRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("goods-receipts/{id}")
    suspend fun updateGoodsReceipt(
        @Path("id") id: Long,
        @Body body: CreateGoodsReceiptRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("goods-receipts/purchase-order/{purchaseOrderId}")
    suspend fun getPurchaseOrderItemsForGr(@Path("purchaseOrderId") purchaseOrderId: Long): Response<JsonElement>

    @GET("goods-receipts/stock-transfer/{stockTransferId}")
    suspend fun getStockTransferItemsForGr(@Path("stockTransferId") stockTransferId: Long): Response<JsonElement>

    @GET("goods-receipts/sales-return/{salesReturnId}")
    suspend fun getSalesReturnItemsForGr(@Path("salesReturnId") salesReturnId: Long): Response<JsonElement>

    @GET("purchase-orders")
    suspend fun listPurchaseOrders(
        @Query("search") search: String? = null,
        @Query("for_goods_receipt") forGoodsReceipt: Boolean? = null,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("warehouses")
    suspend fun listWarehouses(
        @Query("search") search: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("is_active") isActive: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("warehouses/{id}")
    suspend fun getWarehouse(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("warehouses")
    suspend fun createWarehouse(@Body body: WarehouseUpsertRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("warehouses/{id}")
    suspend fun updateWarehouse(
        @Path("id") id: Long,
        @Body body: WarehouseUpsertRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @DELETE("warehouses/{id}")
    suspend fun deleteWarehouse(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("putaway-tasks")
    suspend fun listPutawayTasks(
        @Query("status") status: String? = null,
        @Query("goods_receipt_id") goodsReceiptId: Long? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-transfers")
    suspend fun listStockTransfers(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("sales-returns")
    suspend fun listSalesReturns(
        @Query("return_status") returnStatus: String? = null,
        @Query("search") search: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("users")
    suspend fun listUsers(
        @Query("search") search: String? = null,
        @Query("for_picker") forPicker: Boolean? = true,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("putaway-tasks/{id}")
    suspend fun getPutawayTask(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @PUT("putaway-tasks/{id}")
    suspend fun updatePutawayTask(@Path("id") id: Long, @Body body: UpdatePutawayRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("putaway-tasks/{id}/start")
    suspend fun startPutawayTask(@Path("id") id: Long): Response<ApiEnvelope<Map<String, Any?>>>

    @GET("inventory-pick-lists")
    suspend fun listPickLists(
        @Query("pick_status") pickStatus: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("inventory-pick-lists/{id}")
    suspend fun getPickList(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @PUT("inventory-pick-lists/{id}")
    suspend fun updatePickList(@Path("id") id: Long, @Body body: UpdatePickListRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("inventory-pick-lists/{id}/pack-cut")
    suspend fun updatePickListPackCut(@Path("id") id: Long, @Body body: UpdatePackCutRequest): Response<ApiEnvelope<JsonElement>>

    @GET("products")
    suspend fun listProducts(
        @Query("search") search: String? = null,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("products/{id}/variations")
    suspend fun getProductVariations(@Path("id") id: Long): Response<JsonElement>

    @GET("warehouse-locations")
    suspend fun listWarehouseLocations(
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("search") search: String? = null,
        @Query("is_active") isActive: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("warehouse-locations/{id}")
    suspend fun getWarehouseLocation(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("warehouse-locations")
    suspend fun createWarehouseLocation(@Body body: WarehouseLocationUpsertRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("warehouse-locations/{id}")
    suspend fun updateWarehouseLocation(
        @Path("id") id: Long,
        @Body body: WarehouseLocationUpsertRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @DELETE("warehouse-locations/{id}")
    suspend fun deleteWarehouseLocation(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("warehouse-locations/{id}/stocks")
    suspend fun listLocationStocks(
        @Path("id") locationId: Long,
        @Query("search") search: String? = null,
        @Query("non_zero_only") nonZeroOnly: Boolean? = true,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("warehouses/{warehouseId}/unlocated-stocks")
    suspend fun listUnlocatedStocks(
        @Path("warehouseId") warehouseId: Long,
        @Query("search") search: String? = null,
        @Query("non_zero_only") nonZeroOnly: Boolean? = true,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-adjustments")
    suspend fun listStockAdjustments(
        @Query("status") status: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-adjustments/{id}")
    suspend fun getStockAdjustment(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-adjustments")
    suspend fun createStockAdjustment(@Body body: CreateStockAdjustmentRequest): Response<ApiEnvelope<JsonElement>>

    @DELETE("stock-adjustments/{id}")
    suspend fun deleteStockAdjustment(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-adjustments/{id}/approve")
    suspend fun approveStockAdjustment(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-adjustments/{id}/reject")
    suspend fun rejectStockAdjustment(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("inventory/product-stock/{productId}")
    suspend fun getProductStockQuantity(
        @Path("productId") productId: Long,
        @Query("warehouse_id") warehouseId: Long,
        @Query("variation_id") variationId: Long? = null,
        @Query("warehouse_location_id") warehouseLocationId: Long? = null,
        @Query("batch_number") batchNumber: String? = null,
        @Query("roll_number") rollNumber: String? = null,
    ): Response<JsonElement>

    @GET("stock-relocations")
    suspend fun listStockRelocations(
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-relocations/{id}")
    suspend fun getStockRelocation(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-relocations")
    suspend fun createStockRelocation(@Body body: CreateStockRelocationRequest): Response<ApiEnvelope<JsonElement>>

    @POST("stock-relocations/{id}/approve")
    suspend fun approveStockRelocation(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-relocations/{id}/reject")
    suspend fun rejectStockRelocation(
        @Path("id") id: Long,
        @Body body: RejectStockRelocationRequest? = null,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-relocations/{productId}/stock")
    suspend fun getRelocationProductStock(
        @Path("productId") productId: Long,
        @Query("warehouse_id") warehouseId: Long,
        @Query("from_warehouse_location_id") fromWarehouseLocationId: Long? = null,
        @Query("variation_id") variationId: Long? = null,
    ): Response<JsonElement>

    @GET("stock-opnames")
    suspend fun listStockOpnames(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("warehouse_id") warehouseId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("stock-opnames/{id}")
    suspend fun getStockOpname(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-opnames")
    suspend fun createStockOpname(@Body body: CreateStockOpnameRequest): Response<ApiEnvelope<JsonElement>>

    @GET("stock-opnames/stock-check/{id}")
    suspend fun startStockOpnameCheck(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-opnames/stock-check/{id}")
    suspend fun saveStockOpnameCheck(
        @Path("id") id: Long,
        @Body body: SaveStockOpnameCheckRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @POST("stock-opnames/{id}/done-counting")
    suspend fun doneStockOpnameCounting(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @POST("stock-opnames/{id}/approve")
    suspend fun approveStockOpname(@Path("id") id: Long): Response<JsonElement>

    @POST("stock-opnames/{id}/rfid-count-confirm")
    suspend fun stockOpnameRfidCountConfirm(
        @Path("id") id: Long,
        @Body body: WorkflowScanRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @POST
    suspend fun postRaw(@Url url: String, @Body body: RequestBody): Response<ResponseBody>

    @PUT
    suspend fun putRaw(@Url url: String, @Body body: RequestBody): Response<ResponseBody>
}

data class RefreshTokenResponse(
    val access_token: String?,
    val token_type: String?,
)

data class VoidTagRequest(
    val reason: String? = null,
)

data class LoginRequest(
    val email: String,
    val password: String,
    val two_factor_code: String? = null,
)

data class LoginUser(
    val id: Long?,
    val name: String?,
    val email: String?,
)

data class LoginResponse(
    val access_token: String?,
    val token_type: String?,
    val user: LoginUser?,
    val business_units: List<BusinessUnitDto>?,
    val mobile_permissions: JsonObject? = null,
    val message: String?,
)

data class BusinessUnitDto(
    val id: Long,
    val name: String,
    val team_id: Long?,
    val team_name: String? = null,
) {
    fun toOption() = BusinessUnitOption(
        id = id,
        name = name,
        teamId = team_id,
        teamName = team_name,
    )
}

data class CurrentUserResponse(
    val success: Boolean?,
    val data: LoginUser?,
    val business_units: List<BusinessUnitDto>?,
    val mobile_permissions: JsonObject? = null,
)

data class PaginationMeta(
    val current_page: Int? = null,
    val per_page: Int? = null,
    val total: Int? = null,
    val last_page: Int? = null,
)

data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val data: T? = null,
    val pagination: PaginationMeta? = null,
)

data class ResolveRequest(val epc: String)
data class ResolveResult(val epc: String?, val resolved: Boolean?, val info: Map<String, Any?>?)
data class ResolveBulkRequest(val epcs: List<String>)
data class ResolveBulkResult(val results: List<ResolveResult>?, val summary: Map<String, Int>?)

data class ReadItem(
    val epc: String,
    val rssi: Int? = null,
    val idempotency_key: String? = null,
    val read_at: String? = null,
)

data class ReadsRequest(val session_id: String?, val workflow: String?, val reads: List<ReadItem>)
data class ReadsResult(val inserted: Int?, val skipped: Int?)

data class RegisterTagRequest(
    val epc: String,
    val product_id: Long? = null,
    val variation_value_id: Long? = null,
    val product_stock_id: Long? = null,
    val output_label_id: Long? = null,
    val warehouse_location_id: Long? = null,
)

data class EncodeRequest(
    val product_id: Long? = null,
    val product_stock_id: Long? = null,
    val output_label_id: Long? = null,
    val variation_value_id: Long? = null,
    val roll_number: String? = null,
    val roll_length: Double? = null,
    val batch_number: String? = null,
    val expiry_date: String? = null,
)
data class EncodeJobResult(val job_id: Long?, val epc: String?, val tag: Map<String, Any?>? = null)
data class EncodeConfirmRequest(val job_id: Long, val epc_written: String, val success: Boolean, val error_message: String? = null)

data class FirmwareCheckResult(
    val model: String? = null,
    val current_version: String? = null,
    val latest_version: String? = null,
    val update_available: Boolean = false,
    val download_url: String? = null,
    val file_name: String? = null,
    val support_page: String? = null,
    val checked_at: String? = null,
    val catalog_source: String? = null,
    val catalog_message: String? = null,
)

data class StartSessionRequest(val warehouse_location_id: Long? = null)
data class SessionWrapper(val session: Map<String, Any?>?)
data class SubmitSessionRequest(val epcs: List<String>)
data class SubmitSessionResult(val session: Map<String, Any?>?, val variance: Map<String, Any?>?)

data class WorkflowScanRequest(val epcs: List<String>, val session_id: String? = null)
data class PutawayConfirmRequest(
    val epcs: List<String>,
    val warehouse_location_id: Long? = null,
    val session_id: String? = null,
)

data class CreateGoodsReceiptItem(
    val goods_receipt_item_id: Long? = null,
    val product_id: Long,
    val product_name: String,
    val product_sku: String? = null,
    val ordered_quantity: Double? = null,
    val received_quantity: Double? = null,
    val accepted_quantity: Double? = null,
    val rejected_quantity: Double? = null,
    val purchase_order_item_id: Long? = null,
    val stock_transfer_item_id: Long? = null,
    val sales_return_item_id: Long? = null,
    val variation_value_id: Long? = null,
    val quality_status: String? = "approved",
    val roll_number: String? = null,
    val roll_length: Double? = null,
    val packing_list_item_id: Long? = null,
)

data class CreateGoodsReceiptRequest(
    val source_type: String,
    val source_id: Long,
    val supplier_id: Long? = null,
    val warehouse_id: Long,
    val receipt_date: String,
    val received_by: Long,
    val delivery_note_number: String? = null,
    val vehicle_number: String? = null,
    val driver_name: String? = null,
    val notes: String? = null,
    val total_items: Int,
    val total_received_quantity: Double,
    val items: List<CreateGoodsReceiptItem>,
)

data class PutawayLocationRow(
    val id: Long? = null,
    val warehouse_location_id: Long? = null,
    val quantity_putaway: Double? = null,
)

data class PutawayItemUpdate(
    val id: Long,
    val locations: List<PutawayLocationRow>? = null,
    val notes: String? = null,
)

data class UpdatePutawayRequest(
    val items: List<PutawayItemUpdate>,
    val complete_task: String? = null,
)

data class UpdatePickListRequest(
    val picked_quantities: Map<String, Double>,
    val pick_status: String? = null,
    val notes: String? = null,
)

data class CreateStockOpnameRequest(
    val warehouse_id: Long,
    val warehouse_location_id: Long? = null,
    val opname_date: String,
    val opname_type: String,
    val notes: String? = null,
)

data class SaveStockOpnameCheckRequest(val items: List<StockOpnameCheckItem>)

data class StockOpnameCheckItem(
    val item_id: Long? = null,
    val product_id: Long,
    val variation_value_id: Long? = null,
    val warehouse_location_id: Long? = null,
    val system_quantity: Double? = null,
    val counted_quantity: Double? = null,
    val batch_number: String? = null,
    val expiry_date: String? = null,
    val roll_length: Double? = null,
    val roll_number: String? = null,
    val notes: String? = null,
)

data class UpdatePackCutRequest(
    val packed_quantities: Map<String, Map<String, Double>>? = null,
    val cut_lengths: Map<String, String>? = null,
    val packed_container_quantities: Map<String, Map<String, Double>>? = null,
    val cutting_notes: String? = null,
    val packing_notes: String? = null,
    val complete_cutting: Boolean? = null,
)

data class WarehouseUpsertRequest(
    val name: String,
    val code: String,
    val address: String,
    val city_id: Long? = null,
    val state_id: Long? = null,
    val country_id: Long? = null,
    val phone: String? = null,
    val email: String? = null,
    val capacity: Double? = null,
    val warehouse_type: String? = null,
    val is_active: Boolean = true,
    val copy_address_from_warehouse_id: Long? = null,
)

data class WarehouseLocationUpsertRequest(
    val warehouse_id: Long,
    val zone_code: String,
    val zone_name: String,
    val aisle: String? = null,
    val rack: String? = null,
    val shelf: String? = null,
    val bin: String? = null,
    val location_type: String,
    val capacity: Double? = null,
    val is_active: Boolean = true,
)

data class CreateStockAdjustmentRequest(
    val adjustment_date: String,
    val warehouse_id: Long,
    val adjustment_type: String,
    val reason: String,
    val notes: String? = null,
    val product_id: Long,
    val variation_value_id: Long? = null,
    val warehouse_location_id: Long? = null,
    val batch_number: String? = null,
    val roll_number: String? = null,
    val current_quantity: Double? = null,
    val adjustment_quantity: Double,
    val new_quantity: Double? = null,
    val item_notes: String? = null,
)

data class CreateStockRelocationRequest(
    val warehouse_id: Long,
    val from_warehouse_location_id: Long? = null,
    val to_warehouse_location_id: Long? = null,
    val relocation_date: String,
    val reason: String,
    val notes: String? = null,
    val products: List<RelocationProductPayload>,
)

data class RelocationProductPayload(
    val id: Long,
    val type: String,
    val quantity: Double,
    val variation_value_id: Long? = null,
    val roll_data: RelocationRollDataPayload? = null,
    val variations: List<RelocationVariationPayload>? = null,
)

data class RelocationRollDataPayload(
    val rolls: List<RelocationRollLinePayload>,
)

data class RelocationRollLinePayload(
    val roll_number: String?,
    val used_length: Double,
)

data class RelocationVariationPayload(
    val variation_value_id: Long,
    val quantity: Double,
)

data class RejectStockRelocationRequest(
    val rejection_reason: String? = null,
)

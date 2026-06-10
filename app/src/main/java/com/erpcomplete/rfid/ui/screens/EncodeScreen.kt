package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.EncodeConfirmRequest
import com.erpcomplete.rfid.data.remote.EncodeRequest
import com.erpcomplete.rfid.rfid.TagWriteResult
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowListTable
import com.erpcomplete.rfid.ui.components.WorkflowScanSection
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.PickerMappers
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.long
import kotlinx.coroutines.launch

@Composable
fun EncodeScreen(container: AppContainer, onBack: () -> Unit) {
    var jobId by remember { mutableStateOf<Long?>(null) }
    var epcToWrite by remember { mutableStateOf("") }
    var sourceTagEpc by remember { mutableStateOf<String?>(null) }
    var writeDone by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }

    var selectedProduct by remember { mutableStateOf<PickerOption?>(null) }
    var selectedVariation by remember { mutableStateOf<PickerOption?>(null) }
    var productPickerOpen by remember { mutableStateOf(false) }
    var variationPickerOpen by remember { mutableStateOf(false) }
    var productOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var variationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    var isRollProduct by remember { mutableStateOf(false) }
    var rollNumber by remember { mutableStateOf("") }
    var rollLength by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }

    val tags by container.rfidManager.scannedTags.collectAsState()
    val isConnected by container.rfidManager.isDeviceConnected.collectAsState()
    val warehouseId by container.authStore.warehouseId.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val requiresVariation = variationOptions.isNotEmpty()
    val canRequestEncode = selectedProduct != null &&
        warehouseId != null &&
        (!requiresVariation || selectedVariation != null) &&
        (!isRollProduct || rollNumber.isNotBlank())

    DisposableEffect(Unit) {
        container.rfidManager.clearScannedTags()
        onDispose { container.rfidManager.clearScannedTags() }
    }

    fun resetWriteFlow() {
        sourceTagEpc = null
        writeDone = false
        container.rfidManager.clearScannedTags()
    }

    fun resetEncodeJob() {
        jobId = null
        epcToWrite = ""
        resetWriteFlow()
    }

    fun loadProducts(query: String) {
        scope.launch {
            pickerLoading = true
            runCatching {
                val res = container.api.listProducts(search = query.ifBlank { null }, perPage = 60)
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                productOptions = WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::product)
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    fun loadVariations(productId: Long) {
        scope.launch {
            pickerLoading = true
            runCatching {
                val res = container.api.getProductVariations(productId)
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                val body = res.body()?.asJsonObject
                isRollProduct = body?.get("product_type")?.asString == "roll"
                variationOptions = body?.getAsJsonArray("variations")?.mapNotNull { el ->
                    val v = el.asJsonObject
                    val id = v.long("id") ?: return@mapNotNull null
                    val attrs = v.getAsJsonArray("descriptorValues") ?: v.getAsJsonArray("attributes")
                    val attrText = attrs?.mapNotNull { a ->
                        val ao = a.asJsonObject
                        val name = ao.get("name")?.asString
                            ?: ao.getAsJsonObject("variationDescriptor")?.get("name")?.asString
                        val value = ao.get("value")?.asString
                        if (!name.isNullOrBlank() && !value.isNullOrBlank()) "$name: $value" else null
                    }?.joinToString(" · ")
                    val main = v.get("value")?.asString ?: v.get("display_label")?.asString
                    val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank { "Variation #$id" }
                    PickerOption(id, title, attrText)
                } ?: emptyList()
                if (variationOptions.isEmpty()) selectedVariation = null
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    ErpScaffold(
        title = "Encode tag",
        subtitle = "Choose product → request EPC → write → verify → confirm",
        onBack = onBack,
    ) {
        message?.let {
            StatusBanner(it, isError = it.contains("fail", true) || it.contains("error", true))
        }

        if (!isConnected) {
            StatusBanner("Connect the RFD90 reader before encoding.", isError = true)
        }
        if (warehouseId == null) {
            StatusBanner("Select a workspace warehouse (Home or Settings) before encoding.", isError = true)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ErpCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Product identity",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SearchablePickerField(
                        label = "Product",
                        selected = selectedProduct,
                        placeholder = "Choose product",
                        onOpen = {
                            productPickerOpen = true
                            loadProducts("")
                        },
                        onClear = {
                            selectedProduct = null
                            selectedVariation = null
                            variationOptions = emptyList()
                            isRollProduct = false
                            rollNumber = ""
                            rollLength = ""
                            batchNumber = ""
                            expiryDate = ""
                            resetEncodeJob()
                        },
                    )
                    if (selectedProduct != null) {
                        SearchablePickerField(
                            label = "Variation",
                            selected = selectedVariation,
                            placeholder = when {
                                variationOptions.isEmpty() -> "No variations"
                                else -> "Choose variation"
                            },
                            onOpen = {
                                selectedProduct?.id?.let { loadVariations(it) }
                                variationPickerOpen = true
                            },
                            onClear = {
                                selectedVariation = null
                                resetEncodeJob()
                            },
                        )
                        if (isRollProduct) {
                            OutlinedTextField(
                                value = rollNumber,
                                onValueChange = {
                                    rollNumber = it
                                    resetEncodeJob()
                                },
                                label = { Text("Roll number") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = rollLength,
                                onValueChange = {
                                    rollLength = it
                                    resetEncodeJob()
                                },
                                label = { Text("Roll length (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        OutlinedTextField(
                            value = batchNumber,
                            onValueChange = {
                                batchNumber = it
                                resetEncodeJob()
                            },
                            label = { Text("Batch number (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = {
                                expiryDate = it
                                resetEncodeJob()
                            },
                            label = { Text("Expiry date (optional, YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            WorkflowScanSection(container, tags, onClear = {
                container.rfidManager.clearScannedTags()
                sourceTagEpc = null
                writeDone = false
            })

            ErpPrimaryButton(
                text = "1. Request EPC from server",
                enabled = canRequestEncode,
                onClick = {
                    scope.launch {
                        runCatching {
                            resetWriteFlow()
                            val productId = selectedProduct?.id ?: error("Choose a product")
                            val res = container.api.requestEncode(
                                EncodeRequest(
                                    product_id = productId,
                                    variation_value_id = selectedVariation?.id,
                                    roll_number = rollNumber.trim().ifBlank { null },
                                    roll_length = rollLength.trim().toDoubleOrNull(),
                                    batch_number = batchNumber.trim().ifBlank { null },
                                    expiry_date = expiryDate.trim().ifBlank { null },
                                ),
                            )
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            jobId = res.body()?.data?.job_id
                            epcToWrite = res.body()?.data?.epc?.trim()?.uppercase().orEmpty()
                            if (epcToWrite.isBlank()) error("Server returned empty EPC")
                            val summary = buildString {
                                append("EPC to write: $epcToWrite")
                                selectedProduct?.title?.let { append(" · $it") }
                                selectedVariation?.title?.let { append(" · $it") }
                                if (isRollProduct && rollNumber.isNotBlank()) append(" · Roll $rollNumber")
                            }
                            summary
                        }.onSuccess { message = it }.onFailure { message = it.message }
                    }
                },
            )

            if (epcToWrite.isNotBlank()) {
                Text(
                    "Target EPC",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    epcToWrite,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            ErpPrimaryButton(
                text = if (sourceTagEpc == null) "2. Use latest scan as blank tag" else "Reselect blank tag from scan",
                enabled = tags.isNotEmpty(),
                onClick = {
                    val picked = tags.firstOrNull()?.epc
                    if (picked.isNullOrBlank()) {
                        message = "Hold trigger and scan the blank tag first."
                    } else {
                        sourceTagEpc = picked
                        writeDone = false
                        message = "Source tag: $picked — tap Write EPC to program."
                    }
                },
            )

            ErpPrimaryButton(
                text = if (writing) "Writing…" else "3. Write EPC to tag on device",
                enabled = isConnected && !writing && sourceTagEpc != null && epcToWrite.isNotBlank(),
                onClick = {
                    val source = sourceTagEpc ?: return@ErpPrimaryButton
                    scope.launch {
                        writing = true
                        val result = container.rfidManager.writeEpcToTag(source, epcToWrite)
                        writing = false
                        when (result) {
                            is TagWriteResult.Success -> {
                                writeDone = true
                                container.rfidManager.clearScannedTags()
                                message = "Written ${result.writtenEpc}. Scan again to verify, then confirm."
                            }
                            is TagWriteResult.Failure -> message = result.message
                        }
                    }
                },
            )

            WorkflowListTable(
                columns = listOf("Scanned EPC" to 2f, "Match" to 1f),
                rows = tags.map { tag ->
                    val match = when {
                        !writeDone -> "—"
                        tag.epc.equals(epcToWrite, ignoreCase = true) -> "OK"
                        else -> "?"
                    }
                    listOf(tag.epc, match)
                },
                emptyText = "Hold trigger to scan tags after writing.",
            )

            ErpPrimaryButton(
                text = "4. Confirm encode on server",
                enabled = jobId != null && writeDone && tags.any { it.epc.equals(epcToWrite, ignoreCase = true) },
                onClick = {
                    scope.launch {
                        runCatching {
                            val jid = jobId ?: error("Request EPC first")
                            val written = tags.firstOrNull { it.epc.equals(epcToWrite, ignoreCase = true) }?.epc
                                ?: epcToWrite
                            val res = container.api.confirmEncode(
                                EncodeConfirmRequest(jid, written, true),
                            )
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            res.body()?.message ?: "Encoded"
                        }.onSuccess {
                            message = it
                            resetEncodeJob()
                        }.onFailure { message = it.message }
                    }
                },
            )
        }
    }

    SearchablePickerSheet(
        visible = productPickerOpen,
        title = "Product",
        options = productOptions,
        loading = pickerLoading,
        onDismiss = { productPickerOpen = false },
        onSelect = {
            selectedProduct = it
            selectedVariation = null
            variationOptions = emptyList()
            isRollProduct = false
            rollNumber = ""
            rollLength = ""
            resetEncodeJob()
            it.id.let { id -> loadVariations(id) }
            productPickerOpen = false
        },
        onSearch = { loadProducts(it) },
        searchHint = "Search product…",
    )
    SearchablePickerSheet(
        visible = variationPickerOpen,
        title = "Variation",
        options = variationOptions,
        loading = pickerLoading,
        onDismiss = { variationPickerOpen = false },
        onSelect = {
            selectedVariation = it
            resetEncodeJob()
            variationPickerOpen = false
        },
        onSearch = { selectedProduct?.id?.let { loadVariations(it) } },
        searchHint = "Filter variations…",
    )
}

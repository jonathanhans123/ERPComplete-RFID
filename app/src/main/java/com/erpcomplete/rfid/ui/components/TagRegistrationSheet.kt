package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.RegisterTagRequest
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.string
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagRegistrationSheet(
    visible: Boolean,
    code: String?,
    container: AppContainer,
    onDismiss: () -> Unit,
    onRegistered: (String) -> Unit,
) {
    if (!visible || code.isNullOrBlank()) return

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProduct by remember(code) { mutableStateOf<PickerOption?>(null) }
    var selectedVariation by remember(code) { mutableStateOf<PickerOption?>(null) }
    var productPickerOpen by remember { mutableStateOf(false) }
    var variationPickerOpen by remember { mutableStateOf(false) }
    var productOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var variationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var variationsRequired by remember { mutableStateOf(false) }

    fun loadProducts(query: String) {
        scope.launch {
            pickerLoading = true
            runCatching {
                val res = container.api.listProducts(search = query.ifBlank { null }, perPage = 60)
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                productOptions = WorkflowJson.envelopeList(res).mapNotNull { row ->
                    val id = row.long("id") ?: return@mapNotNull null
                    PickerOption(
                        id = id,
                        title = row.string("name") ?: "Product #$id",
                        subtitle = row.string("sku"),
                    )
                }
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
                val variations = body?.getAsJsonArray("variations")
                variationOptions = variations?.mapNotNull { el ->
                    val v = el.asJsonObject
                    val id = v.long("id") ?: return@mapNotNull null
                    val attrs = v.getAsJsonArray("descriptorValues")
                        ?: v.getAsJsonArray("attributes")
                    val attrText = attrs?.mapNotNull { a ->
                        val ao = a.asJsonObject
                        val name = ao.get("name")?.asString
                            ?: ao.getAsJsonObject("variationDescriptor")?.get("name")?.asString
                        val value = ao.get("value")?.asString
                        if (!name.isNullOrBlank() && !value.isNullOrBlank()) "$name: $value" else null
                    }?.joinToString(" · ")
                    val main = v.get("value")?.asString ?: v.get("display_label")?.asString
                    val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank { "Variation #$id" }
                    PickerOption(id = id, title = title, subtitle = attrText)
                } ?: emptyList()
                variationsRequired = variationOptions.isNotEmpty()
                if (!variationsRequired) selectedVariation = null
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    LaunchedEffect(code) {
        selectedProduct = null
        selectedVariation = null
        message = null
        loadProducts("")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Register tag", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                code.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "This code is not in ERP yet. Link it to a product so workflows can use it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Choose", true)) }

            SearchablePickerField(
                label = "Product",
                selected = selectedProduct,
                placeholder = "Search product name or SKU",
                onOpen = { productPickerOpen = true },
                onClear = {
                    selectedProduct = null
                    selectedVariation = null
                    variationOptions = emptyList()
                    variationsRequired = false
                },
            )
            if (variationsRequired || variationOptions.isNotEmpty()) {
                SearchablePickerField(
                    label = "Variation",
                    selected = selectedVariation,
                    placeholder = "Choose variation / descriptors",
                    enabled = selectedProduct != null,
                    onOpen = { variationPickerOpen = true },
                    onClear = { selectedVariation = null },
                )
            }

            ErpPrimaryButton(
                text = "Save & link tag",
                loading = saving,
                onClick = {
                    scope.launch {
                        saving = true
                        runCatching {
                            val productId = selectedProduct?.id ?: error("Choose a product")
                            if (variationsRequired && selectedVariation == null) {
                                error("Choose a variation for this product")
                            }
                            val res = container.api.registerTag(
                                RegisterTagRequest(
                                    epc = code.trim().uppercase(),
                                    product_id = productId,
                                    variation_value_id = selectedVariation?.id,
                                ),
                            )
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            onRegistered(code.trim().uppercase())
                            onDismiss()
                        }.onFailure { message = it.message }
                        saving = false
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
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
            loadVariations(it.id)
        },
        onSearch = { loadProducts(it) },
        searchHint = "Search name or SKU…",
    )
    SearchablePickerSheet(
        visible = variationPickerOpen,
        title = "Variation",
        options = variationOptions,
        loading = pickerLoading,
        onDismiss = { variationPickerOpen = false },
        onSelect = { selectedVariation = it },
        searchHint = "Filter variations…",
    )
}

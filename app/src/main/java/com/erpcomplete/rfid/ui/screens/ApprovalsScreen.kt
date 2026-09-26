package com.erpcomplete.rfid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.ApprovalDecisionRequest
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.SortableCardListToolbar
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowListCardSkeleton
import com.erpcomplete.rfid.ui.components.applyCardListSortSearch
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.ui.components.rememberWorkflowLiveList
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.WorkflowJson.boolean
import com.erpcomplete.rfid.util.WorkflowJson.envelopePage
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.launchWorkflow
import com.google.gson.JsonObject

/** ERP approval types a warehouse handheld can act on; other documents stay on the web app. */
internal val WAREHOUSE_APPROVAL_TYPES = listOf(
    "stock_adjustment",
    "stock_relocation",
    "stock_opname",
    "stock_transfer",
    "goods_receipt",
    "pick_list",
)

private data class PendingDecision(val row: JsonObject, val approve: Boolean)

@Composable
fun ApprovalsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emDash = stringResource(R.string.symbol_em_dash)
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listMyApprovals(WAREHOUSE_APPROVAL_TYPES, page)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    var decision by remember { mutableStateOf<PendingDecision?>(null) }

    val sortLabels = listOf(
        stringResource(R.string.col_number),
        stringResource(R.string.label_type),
        stringResource(R.string.label_date),
    )
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.string("document_number") ?: "" },
                { it.string("approval_type_label") ?: "" },
                { it.string("created_at") ?: "" },
            ),
        )
    }

    ErpScaffold(
        title = stringResource(R.string.approvals_title),
        subtitle = stringResource(R.string.approvals_subtitle),
        onBack = onBack,
    ) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = stringResource(R.string.search_approvals_hint),
        )
        when {
            liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
            visibleRows.isEmpty() -> Text(
                stringResource(R.string.approvals_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> visibleRows.forEach { row ->
                ErpCard {
                    Text(
                        listOfNotNull(row.string("approval_type_label"), row.string("document_number") ?: emDash)
                            .joinToString(" · "),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        listOfNotNull(
                            row.string("requested_by_name")?.let { context.getString(R.string.approvals_requested_by, it) },
                            row.string("priority_label"),
                            row.string("created_at")?.take(10),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    row.string("approval_reason")?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row.boolean("can_approve") == true) {
                            ErpPrimaryButton(
                                text = stringResource(R.string.action_approve),
                                modifier = Modifier.weight(1f),
                                onClick = { decision = PendingDecision(row, approve = true) },
                            )
                        }
                        if (row.boolean("can_reject") == true) {
                            OutlinedButton(
                                onClick = { decision = PendingDecision(row, approve = false) },
                                modifier = Modifier.weight(1f).height(46.dp),
                            ) { Text(stringResource(R.string.action_reject)) }
                        }
                    }
                }
            }
        }
    }

    decision?.let { pending ->
        var text by remember(pending) { mutableStateOf("") }
        var submitting by remember(pending) { mutableStateOf(false) }
        var dialogError by remember(pending) { mutableStateOf<String?>(null) }
        val id = pending.row.long("id")
        val label = pending.row.string("document_number") ?: pending.row.string("approval_type_label") ?: emDash
        val reasonRequired = stringResource(R.string.approvals_reason_required)
        AlertDialog(
            onDismissRequest = { if (!submitting) decision = null },
            title = {
                Text(
                    stringResource(
                        if (pending.approve) R.string.approvals_approve_title else R.string.approvals_reject_title,
                        label,
                    ),
                )
            },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; dialogError = null },
                        label = {
                            Text(
                                stringResource(
                                    if (pending.approve) R.string.approvals_notes_label else R.string.approvals_reason_label,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && id != null,
                    onClick = {
                        if (id == null) return@TextButton
                        if (!pending.approve && text.isBlank()) {
                            dialogError = reasonRequired
                            return@TextButton
                        }
                        scope.launchWorkflow({ submitting = it }, { dialogError = it }) {
                            val res = if (pending.approve) {
                                container.api.approveApprovalRequest(id, ApprovalDecisionRequest(approval_notes = text.trim().ifBlank { null }))
                            } else {
                                container.api.rejectApprovalRequest(id, ApprovalDecisionRequest(rejection_reason = text.trim()))
                            }
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            Toast.makeText(
                                context,
                                if (pending.approve) R.string.approvals_approved else R.string.approvals_rejected,
                                Toast.LENGTH_SHORT,
                            ).show()
                            decision = null
                            liveList.refresh()
                            null
                        }
                    },
                ) { Text(stringResource(if (pending.approve) R.string.action_approve else R.string.action_reject)) }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { decision = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

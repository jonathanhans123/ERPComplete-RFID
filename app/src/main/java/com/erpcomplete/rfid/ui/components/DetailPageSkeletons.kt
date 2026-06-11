package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun SkeletonTabRow(tabCount: Int = 2) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(tabCount) {
            SkeletonBox(Modifier.weight(1f), height = 40.dp)
        }
    }
}

@Composable
private fun SkeletonScanPanel(modifier: Modifier = Modifier) {
    ErpCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(height = 14.dp, widthFraction = 0.35f)
            SkeletonBox(height = 52.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.weight(1f), height = 36.dp)
                SkeletonBox(Modifier.weight(0.4f), height = 36.dp)
            }
            repeat(2) {
                SkeletonBox(height = 12.dp, widthFraction = 0.9f)
            }
        }
    }
}

@Composable
private fun SkeletonLineEditBlock(
    fieldCount: Int = 2,
    withPicker: Boolean = false,
) {
    SkeletonBox(height = 14.dp, widthFraction = 0.72f)
    if (withPicker) {
        SkeletonBox(height = 52.dp)
    }
    when (fieldCount) {
        1 -> SkeletonBox(height = 52.dp)
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.weight(1f), height = 52.dp)
            SkeletonBox(Modifier.weight(1f), height = 52.dp)
        }
    }
}

/** Goods receipt detail: source meta, putaway CTA, tabs, header form, items table, line edits, RFID scan. */
@Composable
fun GoodsReceiptDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBox(height = 14.dp, widthFraction = 0.88f)
        SkeletonBox(height = 46.dp)
        SkeletonTabRow()
        ErpCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(height = 18.dp, widthFraction = 0.45f)
                SkeletonBox(height = 12.dp, widthFraction = 0.55f)
                Spacer(Modifier.height(4.dp))
                repeat(6) {
                    SkeletonBox(height = 52.dp)
                }
            }
        }
        SkeletonBox(height = 46.dp)
        WorkflowTableSkeleton(rows = 4, columns = 6)
        repeat(2) { SkeletonLineEditBlock(fieldCount = 2) }
        SkeletonScanPanel()
        SkeletonBox(height = 46.dp)
    }
}

/** Putaway task detail: items table, per-line location/qty, RFID location, scan panel, action buttons. */
@Composable
fun PutawayDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkflowTableSkeleton(rows = 4, columns = 6)
        repeat(2) { SkeletonLineEditBlock(fieldCount = 1, withPicker = true) }
        SkeletonBox(height = 52.dp)
        SkeletonScanPanel()
        SkeletonBox(height = 46.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.weight(1f), height = 46.dp)
            SkeletonBox(Modifier.weight(1f), height = 46.dp)
        }
    }
}

/** Pick list detail: Pick / Pack & cut tabs, pick table, qty fields, scan, save actions. */
@Composable
fun PickListDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonTabRow()
        WorkflowTableSkeleton(rows = 4, columns = 6)
        repeat(2) { SkeletonBox(height = 52.dp) }
        SkeletonScanPanel()
        SkeletonBox(height = 46.dp)
        SkeletonBox(height = 46.dp)
        SkeletonBox(height = 46.dp)
    }
}

/** Stock opname count: warehouse label, 7-col table, scan, counted qty fields, save / done buttons. */
@Composable
fun StockOpnameDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBox(height = 14.dp, widthFraction = 0.6f)
        WorkflowTableSkeleton(rows = 5, columns = 7)
        SkeletonScanPanel()
        repeat(2) { SkeletonBox(height = 52.dp) }
        SkeletonBox(height = 46.dp)
        SkeletonBox(height = 46.dp)
    }
}

/** Read-only warehouse info: header card + detail fields. */
@Composable
fun WarehouseInfoSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ErpCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(height = 22.dp, widthFraction = 0.55f)
                SkeletonBox(height = 14.dp, widthFraction = 0.35f)
                SkeletonBox(height = 14.dp, widthFraction = 0.25f)
            }
        }
        ErpCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(7) {
                    SkeletonBox(height = 14.dp, widthFraction = if (it % 2 == 0) 0.3f else 0.75f)
                }
            }
        }
    }
}

/** Stock adjustment detail: header card, line item card, approve/reject + delete actions. */
@Composable
fun AdjustmentDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ErpCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(height = 14.dp, widthFraction = 0.5f)
                SkeletonBox(height = 14.dp, widthFraction = 0.7f)
                SkeletonBox(height = 14.dp, widthFraction = 0.4f)
            }
        }
        ErpCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(height = 18.dp, widthFraction = 0.65f)
                SkeletonBox(height = 12.dp, widthFraction = 0.45f)
                SkeletonBox(height = 12.dp, widthFraction = 0.35f)
                SkeletonBox(height = 16.dp, widthFraction = 0.55f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.weight(1f), height = 46.dp)
            SkeletonBox(Modifier.weight(1f), height = 46.dp)
        }
        SkeletonBox(height = 46.dp)
    }
}

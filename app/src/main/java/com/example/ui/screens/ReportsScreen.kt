package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.ui.theme.EmeraldProfitDark
import com.example.ui.theme.RedExpense
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.DriverStats
import com.example.ui.viewmodel.PeriodFilter
import com.example.ui.viewmodel.VehicleStats
import com.example.util.Formatters

@Composable
fun ReportsScreen(
    stats: DashboardStats,
    vehicleStats: List<VehicleStats>,
    driverStats: List<DriverStats>,
    allBookings: List<BookingEntity>,
    allExpenses: List<ExpenseEntity>,
    allPayments: List<DriverPaymentEntity>,
    selectedPeriod: PeriodFilter,
    currencySymbol: String,
    onPeriodSelected: (PeriodFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedReportTab by remember { mutableIntStateOf(0) } // 0: Financial Summary, 1: Vehicle P&L, 2: Driver Payouts

    fun shareReport() {
        val sb = StringBuilder()
        sb.append("📊 RoadBook P&L Report (${selectedPeriod.label})\n")
        sb.append("------------------------------------\n")
        sb.append("Total Revenue: ${Formatters.formatCurrency(stats.totalIncome, currencySymbol)}\n")
        sb.append("Total Vehicle Expenses: ${Formatters.formatCurrency(stats.totalExpenses, currencySymbol)}\n")
        sb.append("Driver Payouts: ${Formatters.formatCurrency(stats.totalDriverPayments, currencySymbol)}\n")
        sb.append("------------------------------------\n")
        sb.append("NET PROFIT: ${Formatters.formatCurrency(stats.netProfit, currencySymbol)}\n")
        sb.append("Pending Receivables: ${Formatters.formatCurrency(stats.pendingReceivables, currencySymbol)}\n\n")

        sb.append("🚚 Vehicle Breakdown:\n")
        vehicleStats.forEach { vs ->
            sb.append("• ${vs.vehicle.name} (${vs.vehicle.numberPlate}): ")
            sb.append("Rev ${Formatters.formatCurrency(vs.totalIncome, currencySymbol)} | ")
            sb.append("Exp ${Formatters.formatCurrency(vs.totalExpenses, currencySymbol)} | ")
            sb.append("Profit ${Formatters.formatCurrency(vs.netProfit, currencySymbol)}\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share RoadBook Report")
        context.startActivity(shareIntent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Period filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PeriodFilter.values().forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Share Report Button
        Button(
            onClick = { shareReport() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("share_report_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Business Summary (WhatsApp/SMS)")
        }

        Spacer(modifier = Modifier.height(10.dp))

        TabRow(
            selectedTabIndex = selectedReportTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedReportTab == 0,
                onClick = { selectedReportTab = 0 },
                text = { Text("P&L Summary", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedReportTab == 1,
                onClick = { selectedReportTab = 1 },
                text = { Text("Vehicle Report", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedReportTab == 2,
                onClick = { selectedReportTab = 2 },
                text = { Text("Driver Report", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedReportTab) {
            0 -> {
                // High Level P&L Statement
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Statement of Accounts (${selectedPeriod.label})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gross Invoiced Trips (${stats.bookingsCount})")
                                    Text(
                                        Formatters.formatCurrency(stats.totalIncome, currencySymbol),
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldProfitDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Operating & Fuel Expenses")
                                    Text(
                                        "-${Formatters.formatCurrency(stats.totalExpenses, currencySymbol)}",
                                        fontWeight = FontWeight.Bold,
                                        color = RedExpense
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Driver Remuneration")
                                    Text(
                                        "-${Formatters.formatCurrency(stats.totalDriverPayments, currencySymbol)}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Net Realized Profit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        Formatters.formatCurrency(stats.netProfit, currencySymbol),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = if (stats.netProfit >= 0) EmeraldProfitDark else RedExpense
                                    )
                                }

                                if (stats.pendingReceivables > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Uncollected Receivables", color = Color(0xFFD97706), fontSize = 12.sp)
                                        Text(
                                            Formatters.formatCurrency(stats.pendingReceivables, currencySymbol),
                                            color = Color(0xFFD97706),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Fleet Health & Profit Margins",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val margin = if (stats.totalIncome > 0) ((stats.netProfit / stats.totalIncome) * 100).toInt() else 0
                                Text("Profit Margin: $margin% on total turnover", fontSize = 13.sp)
                                Text("Active Fleet Size: ${vehicleStats.size} registered transport units", fontSize = 13.sp)
                                Text("Active Drivers: ${driverStats.size} drivers on payroll/trip pay", fontSize = 13.sp)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(60.dp)) }
                }
            }

            1 -> {
                // Vehicle P&L Report
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vehicleStats, key = { it.vehicle.id }) { vs ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(vs.vehicle.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(vs.vehicle.numberPlate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = "${vs.bookingsCount} trips",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Income: ${Formatters.formatCurrency(vs.totalIncome, currencySymbol)}", fontSize = 12.sp, color = EmeraldProfitDark)
                                    Text("Cost: ${Formatters.formatCurrency(vs.totalExpenses + vs.totalDriverCost, currencySymbol)}", fontSize = 12.sp, color = RedExpense)
                                    Text(
                                        "Profit: ${Formatters.formatCurrency(vs.netProfit, currencySymbol)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (vs.netProfit >= 0) EmeraldProfitDark else RedExpense
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(60.dp)) }
                }
            }

            2 -> {
                // Driver Report
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(driverStats, key = { it.driver.id }) { ds ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(ds.driver.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = if (ds.driver.paymentType == "per_trip") "Per-trip model" else "Salary model",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (ds.assignedVehicle != null) {
                                        Text(
                                            text = "Truck: ${ds.assignedVehicle.name}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Paid", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        Formatters.formatCurrency(ds.totalPaid, currencySymbol),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(60.dp)) }
                }
            }
        }
    }
}

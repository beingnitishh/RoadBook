package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.VehicleEntity
import com.example.ui.components.CategoryChip
import com.example.ui.components.HeroProfitCard
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.SummaryMetricCard
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainerLight
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.EmeraldProfitDark
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RedContainer
import com.example.ui.theme.RedExpense
import com.example.ui.viewmodel.ActivityItem
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.PeriodFilter
import com.example.ui.viewmodel.VehicleStats
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    stats: DashboardStats,
    vehicleStats: List<VehicleStats>,
    recentActivities: List<ActivityItem>,
    selectedPeriod: PeriodFilter,
    currencySymbol: String,
    onPeriodSelected: (PeriodFilter) -> Unit,
    onQuickAddBooking: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickAddPayment: () -> Unit,
    onVehicleClick: (Long) -> Unit,
    onViewAllBookings: () -> Unit,
    onViewAllExpenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Period Selector Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeriodFilter.values().forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        label = { Text(period.label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("period_filter_${period.name}")
                    )
                }
            }
        }

        // Hero Net Profit Card
        item {
            HeroProfitCard(
                netProfit = stats.netProfit,
                totalIncome = stats.totalIncome,
                totalExpenses = stats.totalExpenses,
                totalDriverPayments = stats.totalDriverPayments,
                pendingReceivables = stats.pendingReceivables,
                currencySymbol = currencySymbol,
                periodLabel = selectedPeriod.label
            )
        }

        // Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onQuickAddBooking,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_booking_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Booking", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onQuickAddExpense,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_expense_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Expense", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onQuickAddPayment,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_payment_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pay Driver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Per-Vehicle Performance Comparison
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vehicle P&L Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vehicleStats.size} Vehicles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (vehicleStats.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No vehicles yet. Add your first vehicle to track profit!")
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vehicleStats, key = { it.vehicle.id }) { vStat ->
                            VehicleProfitMiniCard(
                                stat = vStat,
                                currencySymbol = currencySymbol,
                                onClick = { onVehicleClick(vStat.vehicle.id) }
                            )
                        }
                    }
                }
            }
        }

        // Recent Activity Feed Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    Text(
                        text = "Bookings",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onViewAllBookings() }
                            .padding(4.dp)
                    )
                    Text(" • ", color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "Expenses",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onViewAllExpenses() }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (recentActivities.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activities recorded in this period",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = recentActivities,
                key = { activity ->
                    when (activity) {
                        is ActivityItem.BookingActivity -> "b_${activity.booking.id}"
                        is ActivityItem.ExpenseActivity -> "e_${activity.expense.id}"
                        is ActivityItem.DriverPaymentActivity -> "p_${activity.payment.id}"
                    }
                }
            ) { activity ->
                ActivityRowItem(
                    activity = activity,
                    currencySymbol = currencySymbol
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VehicleProfitMiniCard(
    stat: VehicleStats,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val isProfitable = stat.netProfit >= 0
    Card(
        modifier = Modifier
            .width(230.dp)
            .clickable { onClick() }
            .testTag("vehicle_profit_card_${stat.vehicle.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.vehicle.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        text = stat.vehicle.numberPlate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = Formatters.formatCurrency(stat.totalIncome, currencySymbol),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldProfitDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Cost", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = Formatters.formatCurrency(stat.totalExpenses + stat.totalDriverCost, currencySymbol),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedExpense
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Net Profit", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = Formatters.formatCurrency(stat.netProfit, currencySymbol),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isProfitable) EmeraldProfitDark else RedExpense
                )
            }
        }
    }
}

@Composable
fun ActivityRowItem(
    activity: ActivityItem,
    currencySymbol: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, iconBg, iconColor) = when (activity) {
                is ActivityItem.BookingActivity -> Triple(
                    Icons.Default.LocalShipping,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary
                )
                is ActivityItem.ExpenseActivity -> Triple(
                    Icons.Default.ReceiptLong,
                    RedContainer,
                    RedExpense
                )
                is ActivityItem.DriverPaymentActivity -> Triple(
                    Icons.Default.Person,
                    AmberContainerLight,
                    Color(0xFFB45309)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                when (activity) {
                    is ActivityItem.BookingActivity -> {
                        Text(
                            text = activity.booking.customerRoute,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${activity.vehicleName}${activity.driverName?.let { " • Driver: $it" } ?: ""} • ${Formatters.formatRelativeDate(activity.booking.date)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ActivityItem.ExpenseActivity -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${activity.expense.category} Expense",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CategoryChip(category = activity.expense.category)
                        }
                        Text(
                            text = "${activity.vehicleName} • ${Formatters.formatRelativeDate(activity.expense.date)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ActivityItem.DriverPaymentActivity -> {
                        Text(
                            text = "Paid to ${activity.driverName}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Driver Payout • ${Formatters.formatRelativeDate(activity.payment.date)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                when (activity) {
                    is ActivityItem.BookingActivity -> {
                        Text(
                            text = "+${Formatters.formatCurrency(activity.booking.amount, currencySymbol)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = EmeraldProfitDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        PaymentStatusBadge(status = activity.booking.paymentStatus)
                    }
                    is ActivityItem.ExpenseActivity -> {
                        Text(
                            text = "-${Formatters.formatCurrency(activity.expense.amount, currencySymbol)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RedExpense
                        )
                    }
                    is ActivityItem.DriverPaymentActivity -> {
                        Text(
                            text = "-${Formatters.formatCurrency(activity.payment.amount, currencySymbol)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }
        }
    }
}

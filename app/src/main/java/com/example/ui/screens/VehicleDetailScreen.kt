package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity
import com.example.ui.components.CategoryChip
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.VehicleTypeBadge
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldProfitDark
import com.example.ui.theme.RedExpense
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicle: VehicleEntity,
    assignedDriver: DriverEntity?,
    allDrivers: List<DriverEntity> = emptyList(),
    bookings: List<BookingEntity>,
    expenses: List<ExpenseEntity>,
    driverPayments: List<DriverPaymentEntity>,
    currencySymbol: String,
    onBackClick: () -> Unit,
    onEditVehicleClick: () -> Unit,
    onDeleteVehicleClick: () -> Unit,
    onAssignDriver: (driverId: Long?) -> Unit = {},
    onAddBookingForVehicle: () -> Unit,
    onAddExpenseForVehicle: () -> Unit,
    onToggleBookingStatus: (BookingEntity) -> Unit,
    onEditBookingClick: (BookingEntity) -> Unit,
    onEditExpenseClick: (ExpenseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAssignDriverDialog by remember { mutableStateOf(false) }

    // Bookings tab filter (Defaults to Last 20 Days as specified in PRD 5.2 and 7.2)
    var bookingTimeFilter by remember { mutableStateOf("LAST_20_DAYS") }
    var bookingStatusFilter by remember { mutableStateOf("ALL") }

    val twentyDaysAgo = remember {
        System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000
    }

    val filteredBookings = remember(bookings, bookingTimeFilter, bookingStatusFilter) {
        bookings.filter { b ->
            val matchTime = when (bookingTimeFilter) {
                "LAST_20_DAYS" -> b.date >= twentyDaysAgo
                "THIS_MONTH" -> {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                    }
                    b.date >= cal.timeInMillis
                }
                else -> true
            }
            val matchStatus = when (bookingStatusFilter) {
                "PAID" -> b.paymentStatus.equals("PAID", ignoreCase = true)
                "PENDING" -> b.paymentStatus.equals("PENDING", ignoreCase = true)
                else -> true
            }
            matchTime && matchStatus
        }
    }

    // Vehicle lifetime financial metrics (cached to prevent re-summing on every recomposition)
    val totalRevenue = remember(bookings) { bookings.sumOf { it.amount } }
    val totalExpense = remember(expenses) { expenses.sumOf { it.amount } }
    val totalDriverCost = remember(driverPayments) { driverPayments.sumOf { it.amount } }
    val netProfit = totalRevenue - (totalExpense + totalDriverCost)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vehicle.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(vehicle.numberPlate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditVehicleClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Vehicle")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Vehicle", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = onAddBookingForVehicle,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("vehicle_add_booking_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Booking")
                }
            } else if (selectedTabIndex == 1) {
                FloatingActionButton(
                    onClick = onAddExpenseForVehicle,
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("vehicle_add_expense_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Vehicle Overview Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VehicleTypeBadge(type = vehicle.type)
                            if (vehicle.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = vehicle.notes,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (assignedDriver != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAssignDriverDialog = true }
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Driver",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = assignedDriver.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAssignDriverDialog = true }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Assign Driver",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+ Assign Driver",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Profit/Loss ribbon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Revenue (${bookings.size} trips)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatters.formatCurrency(totalRevenue, currencySymbol),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldProfitDark
                            )
                        }

                        Column {
                            Text("Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatters.formatCurrency(totalExpense, currencySymbol),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedExpense
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net Profit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatters.formatCurrency(netProfit, currencySymbol),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (netProfit >= 0) EmeraldProfitDark else RedExpense
                            )
                        }
                    }
                }
            }

            // 3 Tabs: Bookings / Expenses / Assigned Driver & Stats
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Bookings (${bookings.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Expenses (${expenses.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Driver & P&L", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // Bookings Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Filter Bar (PRD: Defaults to last 20 days; filter by date range, payment status)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = bookingTimeFilter == "LAST_20_DAYS",
                                onClick = { bookingTimeFilter = "LAST_20_DAYS" },
                                label = { Text("Last 20 Days", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = bookingTimeFilter == "THIS_MONTH",
                                onClick = { bookingTimeFilter = "THIS_MONTH" },
                                label = { Text("This Month", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = bookingTimeFilter == "ALL",
                                onClick = { bookingTimeFilter = "ALL" },
                                label = { Text("All Time", fontSize = 11.sp) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = bookingStatusFilter == "ALL",
                                onClick = { bookingStatusFilter = "ALL" },
                                label = { Text("All Status", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = bookingStatusFilter == "PAID",
                                onClick = { bookingStatusFilter = "PAID" },
                                label = { Text("Paid Only", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = bookingStatusFilter == "PENDING",
                                onClick = { bookingStatusFilter = "PENDING" },
                                label = { Text("Pending Only", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredBookings.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No bookings found for this vehicle in selected range.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredBookings, key = { it.id }) { booking ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onEditBookingClick(booking) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = booking.customerRoute,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = Formatters.formatDate(booking.date),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                val tripDriver = booking.driverId?.let { dId -> allDrivers.find { it.id == dId } }
                                                if (tripDriver != null) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = "Driver",
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = tripDriver.name,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                if (booking.notes.isNotBlank()) {
                                                    Text(
                                                        text = booking.notes,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = Formatters.formatCurrency(booking.amount, currencySymbol),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = EmeraldProfitDark
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                PaymentStatusBadge(
                                                    status = booking.paymentStatus,
                                                    onClick = { onToggleBookingStatus(booking) }
                                                )
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(70.dp)) }
                            }
                        }
                    }
                }

                1 -> {
                    // Expenses Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Category breakdown chips
                        val categorySums = remember(expenses) {
                            expenses.groupBy { it.category }
                                .mapValues { entry -> entry.value.sumOf { it.amount } }
                        }

                        if (categorySums.isNotEmpty()) {
                            Text(
                                text = "Category Totals",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categorySums.forEach { (cat, sum) ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                            Text(cat, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                Formatters.formatCurrency(sum, currencySymbol),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (expenses.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No expenses logged for this vehicle yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(expenses, key = { it.id }) { expense ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onEditExpenseClick(expense) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CategoryChip(category = expense.category)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = Formatters.formatDate(expense.date),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (expense.notes.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = expense.notes,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "-${Formatters.formatCurrency(expense.amount, currencySymbol)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = RedExpense
                                            )
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(70.dp)) }
                            }
                        }
                    }
                }

                2 -> {
                    // Assigned Driver & P&L Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Assigned Driver",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showAssignDriverDialog = true }) {
                                    Text(if (assignedDriver == null) "+ Assign Driver" else "Change")
                                }
                            }
                        }

                        item {
                            if (assignedDriver == null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "No driver currently assigned to this vehicle.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { showAssignDriverDialog = true }
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Assign Driver")
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = assignedDriver.name,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = assignedDriver.phone,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (assignedDriver.paymentType == "per_trip") "Per-Trip" else "Salary",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Agreed Rate: ${Formatters.formatCurrency(assignedDriver.rate, currencySymbol)} ${if (assignedDriver.paymentType == "per_trip") "/ trip" else "/ month"}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showAssignDriverDialog = true },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Change Driver", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { onAssignDriver(null) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Unassign", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Comprehensive P&L Statement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Gross Invoiced (${bookings.size} trips)")
                                        Text(
                                            Formatters.formatCurrency(totalRevenue, currencySymbol),
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldProfitDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Running & Maintenance Expenses")
                                        Text(
                                            "-${Formatters.formatCurrency(totalExpense, currencySymbol)}",
                                            fontWeight = FontWeight.Bold,
                                            color = RedExpense
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Driver Payouts Allocated")
                                        Text(
                                            "-${Formatters.formatCurrency(totalDriverCost, currencySymbol)}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Net Operating Profit", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            Formatters.formatCurrency(netProfit, currencySymbol),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = if (netProfit >= 0) EmeraldProfitDark else RedExpense
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Vehicle?") },
            text = { Text("Are you sure you want to delete ${vehicle.name} (${vehicle.numberPlate})? This will also remove associated bookings and expenses.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteVehicleClick()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAssignDriverDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDriverDialog = false },
            title = { Text("Assign Driver to ${vehicle.name}", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssignDriver(null)
                                    showAssignDriverDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (assignedDriver == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "None (Unassign Driver)",
                                    fontWeight = if (assignedDriver == null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (assignedDriver == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    items(allDrivers, key = { it.id }) { driver ->
                        val isSelected = assignedDriver?.id == driver.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssignDriver(driver.id)
                                    showAssignDriverDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        driver.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (driver.paymentType == "per_trip") "Per-Trip" else "Salary",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${driver.phone} • Rate: ${Formatters.formatCurrency(driver.rate, currencySymbol)}",
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAssignDriverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

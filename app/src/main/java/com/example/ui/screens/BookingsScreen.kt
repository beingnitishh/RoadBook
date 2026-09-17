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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.VehicleEntity
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.theme.EmeraldProfitDark
import com.example.util.Formatters

@Composable
fun BookingsScreen(
    bookings: List<BookingEntity>,
    vehicles: List<VehicleEntity>,
    drivers: List<DriverEntity> = emptyList(),
    currencySymbol: String,
    onAddBookingClick: () -> Unit,
    onEditBookingClick: (BookingEntity) -> Unit,
    onDeleteBookingClick: (BookingEntity) -> Unit,
    onTogglePaymentStatus: (BookingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicleId by remember { mutableLongStateOf(0L) } // 0 means all
    var dateRangeFilter by remember { mutableStateOf("LAST_20_DAYS") } // Defaults to last 20 days (PRD 5.2)
    var statusFilter by remember { mutableStateOf("ALL") }

    var bookingToDelete by remember { mutableStateOf<BookingEntity?>(null) }

    val vehicleMap = remember(vehicles) { vehicles.associate { it.id to it.name } }
    val driverMap = remember(drivers) { drivers.associate { it.id to it.name } }
    val twentyDaysAgo = remember { System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000 }

    val filteredBookings = remember(bookings, searchQuery, selectedVehicleId, dateRangeFilter, statusFilter, vehicleMap, driverMap) {
        bookings.filter { b ->
            val assignedDriverName = b.driverId?.let { driverMap[it] }
            val matchSearch = searchQuery.isBlank() ||
                    b.customerRoute.contains(searchQuery, ignoreCase = true) ||
                    (vehicleMap[b.vehicleId]?.contains(searchQuery, ignoreCase = true) == true) ||
                    (assignedDriverName?.contains(searchQuery, ignoreCase = true) == true)
            val matchVehicle = selectedVehicleId == 0L || b.vehicleId == selectedVehicleId
            val matchTime = when (dateRangeFilter) {
                "TODAY" -> {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                    }
                    b.date >= cal.timeInMillis
                }
                "LAST_20_DAYS" -> b.date >= twentyDaysAgo
                "THIS_MONTH" -> {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                    }
                    b.date >= cal.timeInMillis
                }
                else -> true
            }
            val matchStatus = when (statusFilter) {
                "PAID" -> b.paymentStatus.equals("PAID", ignoreCase = true)
                "PENDING" -> b.paymentStatus.equals("PENDING", ignoreCase = true)
                else -> true
            }
            matchSearch && matchVehicle && matchTime && matchStatus
        }
    }

    val totalAmount = filteredBookings.sumOf { it.amount }
    val pendingAmount = filteredBookings.filter { it.paymentStatus.equals("PENDING", ignoreCase = true) }.sumOf { it.amount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBookingClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_booking_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Booking")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search by Customer / Route name (PRD 5.2)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_search_input"),
                placeholder = { Text("Search by customer, route, or notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date Filters (Default Last 20 Days)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = dateRangeFilter == "LAST_20_DAYS",
                    onClick = { dateRangeFilter = "LAST_20_DAYS" },
                    label = { Text("Last 20 Days", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = dateRangeFilter == "TODAY",
                    onClick = { dateRangeFilter = "TODAY" },
                    label = { Text("Today", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = dateRangeFilter == "THIS_MONTH",
                    onClick = { dateRangeFilter = "THIS_MONTH" },
                    label = { Text("This Month", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = dateRangeFilter == "ALL",
                    onClick = { dateRangeFilter = "ALL" },
                    label = { Text("All Time", fontSize = 11.sp) }
                )
            }

            // Vehicle Filter Chips
            if (vehicles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedVehicleId == 0L,
                        onClick = { selectedVehicleId = 0L },
                        label = { Text("All Vehicles", fontSize = 11.sp) }
                    )
                    vehicles.forEach { v ->
                        FilterChip(
                            selected = selectedVehicleId == v.id,
                            onClick = { selectedVehicleId = v.id },
                            label = { Text(v.name, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Payment status chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { statusFilter = "ALL" },
                    label = { Text("All Status (${filteredBookings.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = statusFilter == "PAID",
                    onClick = { statusFilter = "PAID" },
                    label = { Text("Paid", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = statusFilter == "PENDING",
                    onClick = { statusFilter = "PENDING" },
                    label = { Text("Pending", fontSize = 11.sp) }
                )
            }

            // Summary strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total: ${Formatters.formatCurrency(totalAmount, currencySymbol)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (pendingAmount > 0) {
                    Text(
                        text = "Pending: ${Formatters.formatCurrency(pendingAmount, currencySymbol)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredBookings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (bookings.isEmpty()) "No bookings recorded yet.\nTap + to add your first trip."
                        else "No bookings match the filter criteria.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBookings, key = { it.id }) { booking ->
                        BookingItemCard(
                            booking = booking,
                            vehicleName = vehicleMap[booking.vehicleId] ?: "Unknown",
                            driverName = booking.driverId?.let { driverMap[it] },
                            currencySymbol = currencySymbol,
                            onToggleStatus = { onTogglePaymentStatus(booking) },
                            onEdit = { onEditBookingClick(booking) },
                            onDelete = { bookingToDelete = booking }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    bookingToDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { bookingToDelete = null },
            title = { Text("Delete Booking?") },
            text = { Text("Are you sure you want to delete the booking for \"${b.customerRoute}\"?") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        onDeleteBookingClick(b)
                        bookingToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { bookingToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BookingItemCard(
    booking: BookingEntity,
    vehicleName: String,
    driverName: String? = null,
    currencySymbol: String,
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("booking_card_${booking.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = vehicleName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = Formatters.formatDate(booking.date),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = booking.customerRoute,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Assigned Driver row
            if (driverName != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Driver",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Driver: $driverName",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (booking.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = booking.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatCurrency(booking.amount, currencySymbol),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EmeraldProfitDark
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tap on badge toggles PAID / PENDING immediately!
                    PaymentStatusBadge(
                        status = booking.paymentStatus,
                        onClick = onToggleStatus
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

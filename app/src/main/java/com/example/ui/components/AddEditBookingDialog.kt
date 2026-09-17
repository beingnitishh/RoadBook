package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.VehicleEntity
import com.example.util.Formatters

@Composable
fun AddEditBookingDialog(
    bookingToEdit: BookingEntity? = null,
    vehicles: List<VehicleEntity>,
    drivers: List<DriverEntity> = emptyList(),
    preselectedVehicleId: Long? = null,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (vehicleId: Long, driverId: Long?, date: Long, customerRoute: String, amount: Double, paymentStatus: String, notes: String) -> Unit
) {
    var selectedVehicleId by remember {
        mutableStateOf(
            bookingToEdit?.vehicleId ?: preselectedVehicleId ?: vehicles.firstOrNull()?.id ?: 0L
        )
    }
    // Default to the driver assigned to this booking, or auto-suggest driver assigned to vehicle
    var selectedDriverId by remember {
        mutableStateOf<Long?>(
            bookingToEdit?.driverId ?: drivers.find { it.assignedVehicleId == (bookingToEdit?.vehicleId ?: preselectedVehicleId ?: vehicles.firstOrNull()?.id) }?.id
        )
    }
    var customerRoute by remember { mutableStateOf(bookingToEdit?.customerRoute ?: "") }
    var amountText by remember { mutableStateOf(bookingToEdit?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var paymentStatus by remember { mutableStateOf(bookingToEdit?.paymentStatus ?: "PAID") }
    var selectedDate by remember { mutableLongStateOf(bookingToEdit?.date ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(bookingToEdit?.notes ?: "") }
    var hasError by remember { mutableStateOf(false) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }
    var driverDropdownExpanded by remember { mutableStateOf(false) }

    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
    val selectedDriver = drivers.find { it.id == selectedDriverId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (bookingToEdit == null) "New Booking / Trip" else "Edit Booking",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vehicle selection
                Text(
                    text = "Vehicle *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVehicle?.let { "${it.name} (${it.numberPlate})" } ?: "Select Vehicle",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Vehicle")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay so tapping field reliably opens dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { vehicleDropdownExpanded = true }
                            .testTag("booking_vehicle_selector")
                    )
                    DropdownMenu(
                        expanded = vehicleDropdownExpanded,
                        onDismissRequest = { vehicleDropdownExpanded = false }
                    ) {
                        vehicles.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.name} (${v.numberPlate})") },
                                onClick = {
                                    selectedVehicleId = v.id
                                    vehicleDropdownExpanded = false
                                    // Auto-suggest vehicle's assigned driver if no driver is explicitly chosen
                                    if (selectedDriverId == null) {
                                        selectedDriverId = drivers.find { it.assignedVehicleId == v.id }?.id
                                    }
                                }
                            )
                        }
                    }
                }

                // Driver selection (Optional)
                Text(
                    text = "Assigned Driver (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDriver?.let { "${it.name} (${it.phone})" } ?: "None (Unassigned)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Driver")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay so tapping field reliably opens dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { driverDropdownExpanded = true }
                            .testTag("booking_driver_selector")
                    )
                    DropdownMenu(
                        expanded = driverDropdownExpanded,
                        onDismissRequest = { driverDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unassigned)", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                selectedDriverId = null
                                driverDropdownExpanded = false
                            }
                        )
                        drivers.forEach { d ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(d.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = "${d.phone} • ${if (d.paymentType == "per_trip") "Per-trip" else "Salary"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDriverId = d.id
                                    driverDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Customer / Route
                OutlinedTextField(
                    value = customerRoute,
                    onValueChange = {
                        customerRoute = it
                        hasError = false
                    },
                    label = { Text("Customer or Route *") },
                    placeholder = { Text("e.g. Reliance / Mumbai to Pune") },
                    singleLine = true,
                    isError = hasError && customerRoute.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_customer_route_input")
                )

                // Trip Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        hasError = false
                    },
                    label = { Text("Trip Amount ($currencySymbol) *") },
                    placeholder = { Text("e.g. 15000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasError && (amountText.toDoubleOrNull() ?: 0.0) <= 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_amount_input")
                )

                // Payment Status
                Text(
                    text = "Payment Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = paymentStatus.equals("PAID", ignoreCase = true),
                        onClick = { paymentStatus = "PAID" },
                        label = { Text("PAID") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentStatus.equals("PENDING", ignoreCase = true),
                        onClick = { paymentStatus = "PENDING" },
                        label = { Text("PENDING") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Date display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip Date: ${Formatters.formatDate(selectedDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("Invoice no, advance received, cargo details...") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_notes_input")
                )

                if (hasError) {
                    Text(
                        text = "Please select vehicle, enter route, and valid amount.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (selectedVehicleId == 0L || customerRoute.isBlank() || amt <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(selectedVehicleId, selectedDriverId, selectedDate, customerRoute, amt, paymentStatus, notes)
                    }
                },
                modifier = Modifier.testTag("save_booking_button")
            ) {
                Text(if (bookingToEdit == null) "Log Booking" else "Save Booking")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

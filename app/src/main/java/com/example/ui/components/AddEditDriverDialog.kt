package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.DriverEntity
import com.example.data.entity.VehicleEntity

@Composable
fun AddEditDriverDialog(
    driverToEdit: DriverEntity? = null,
    vehicles: List<VehicleEntity>,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, paymentType: String, rate: Double, assignedVehicleId: Long?, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(driverToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(driverToEdit?.phone ?: "") }
    var paymentType by remember { mutableStateOf(driverToEdit?.paymentType ?: "per_trip") }
    var rateText by remember { mutableStateOf(driverToEdit?.rate?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var assignedVehicleId by remember { mutableStateOf<Long?>(driverToEdit?.assignedVehicleId) }
    var notes by remember { mutableStateOf(driverToEdit?.notes ?: "") }
    var hasError by remember { mutableStateOf(false) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }

    val assignedVehicle = vehicles.find { it.id == assignedVehicleId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (driverToEdit == null) "Add New Driver" else "Edit Driver",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        hasError = false
                    },
                    label = { Text("Driver Name *") },
                    placeholder = { Text("e.g. Ramesh Kumar") },
                    singleLine = true,
                    isError = hasError && name.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        hasError = false
                    },
                    label = { Text("Phone Number *") },
                    placeholder = { Text("e.g. 9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = hasError && phone.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_phone_input")
                )

                Text(
                    text = "Payment Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = paymentType == "per_trip",
                        onClick = { paymentType = "per_trip" },
                        label = { Text("Per Trip Rate") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentType == "fixed_salary",
                        onClick = { paymentType = "fixed_salary" },
                        label = { Text("Fixed Salary") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        rateText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        hasError = false
                    },
                    label = {
                        Text(
                            if (paymentType == "per_trip") "Rate per Trip ($currencySymbol)"
                            else "Monthly Salary ($currencySymbol)"
                        )
                    },
                    placeholder = { Text(if (paymentType == "per_trip") "e.g. 800" else "e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasError && (rateText.toDoubleOrNull() ?: 0.0) <= 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_rate_input")
                )

                // Assigned Vehicle (Optional)
                Text(
                    text = "Assigned Vehicle (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = assignedVehicle?.let { "${it.name} (${it.numberPlate})" } ?: "None (Unassigned)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Vehicle")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay to reliably intercept touches on readOnly field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { vehicleDropdownExpanded = true }
                            .testTag("driver_vehicle_selector")
                    )
                    DropdownMenu(
                        expanded = vehicleDropdownExpanded,
                        onDismissRequest = { vehicleDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unassigned)", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                assignedVehicleId = null
                                vehicleDropdownExpanded = false
                            }
                        )
                        vehicles.forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${v.name} (${v.numberPlate})", fontWeight = FontWeight.Medium)
                                        Text(v.type, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    assignedVehicleId = v.id
                                    vehicleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("License number, address, experience...") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_notes_input")
                )

                if (hasError) {
                    Text(
                        text = "Please enter driver name, phone, and rate.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = rateText.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || phone.isBlank() || rate <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(name, phone, paymentType, rate, assignedVehicleId, notes)
                    }
                },
                modifier = Modifier.testTag("save_driver_button")
            ) {
                Text(if (driverToEdit == null) "Add Driver" else "Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

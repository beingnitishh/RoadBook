package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.DriverEntity
import com.example.util.Formatters

@Composable
fun LogDriverPaymentDialog(
    drivers: List<DriverEntity>,
    preselectedDriverId: Long? = null,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (driverId: Long, vehicleId: Long?, amount: Double, date: Long, notes: String) -> Unit
) {
    var selectedDriverId by remember {
        mutableStateOf(
            preselectedDriverId ?: drivers.firstOrNull()?.id ?: 0L
        )
    }
    var amountText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var driverDropdownExpanded by remember { mutableStateOf(false) }

    val selectedDriver = drivers.find { it.id == selectedDriverId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Driver Payment",
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
                // Driver selection
                Text(
                    text = "Driver *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDriver?.let { "${it.name} (${if (it.paymentType == "per_trip") "Per-trip" else "Salary"})" } ?: "Select Driver",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Driver")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { driverDropdownExpanded = true }
                            .testTag("driver_payment_selector")
                    )
                    DropdownMenu(
                        expanded = driverDropdownExpanded,
                        onDismissRequest = { driverDropdownExpanded = false }
                    ) {
                        drivers.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.name} (${d.phone})") },
                                onClick = {
                                    selectedDriverId = d.id
                                    driverDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        hasError = false
                    },
                    label = { Text("Amount Paid ($currencySymbol) *") },
                    placeholder = { Text(selectedDriver?.let { "Rate is ${it.rate}" } ?: "e.g. 1500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasError && (amountText.toDoubleOrNull() ?: 0.0) <= 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input")
                )

                // Date
                Text(
                    text = "Payment Date: ${Formatters.formatDate(selectedDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Trip allowance, bonus, monthly advance") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_notes_input")
                )

                if (hasError) {
                    Text(
                        text = "Please select driver and enter valid amount.",
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
                    if (selectedDriverId == 0L || amt <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(selectedDriverId, selectedDriver?.assignedVehicleId, amt, selectedDate, notes)
                    }
                },
                modifier = Modifier.testTag("save_payment_button")
            ) {
                Text("Log Payment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

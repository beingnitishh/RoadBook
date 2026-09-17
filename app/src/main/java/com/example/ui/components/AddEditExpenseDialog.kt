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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity
import com.example.util.Formatters

@Composable
fun AddEditExpenseDialog(
    expenseToEdit: ExpenseEntity? = null,
    vehicles: List<VehicleEntity>,
    preselectedVehicleId: Long? = null,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (vehicleId: Long, category: String, amount: Double, date: Long, notes: String) -> Unit
) {
    var selectedVehicleId by remember {
        mutableStateOf(
            expenseToEdit?.vehicleId ?: preselectedVehicleId ?: vehicles.firstOrNull()?.id ?: 0L
        )
    }
    var selectedCategory by remember { mutableStateOf(expenseToEdit?.category ?: "Fuel") }
    var amountText by remember { mutableStateOf(expenseToEdit?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var selectedDate by remember { mutableLongStateOf(expenseToEdit?.date ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(expenseToEdit?.notes ?: "") }
    var hasError by remember { mutableStateOf(false) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Fuel", "Maintenance", "Toll", "Repair", "Challan/Fine", "Other")
    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (expenseToEdit == null) "Log Vehicle Expense" else "Edit Expense",
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
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { vehicleDropdownExpanded = true }
                            .testTag("expense_vehicle_selector")
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
                                }
                            )
                        }
                    }
                }

                // Expense Category
                Text(
                    text = "Category *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory.equals(cat, ignoreCase = true),
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory.equals(cat, ignoreCase = true),
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                // Expense Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        hasError = false
                    },
                    label = { Text("Amount ($currencySymbol) *") },
                    placeholder = { Text("e.g. 4500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasError && (amountText.toDoubleOrNull() ?: 0.0) <= 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                // Date
                Text(
                    text = "Date: ${Formatters.formatDate(selectedDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Bill Details") },
                    placeholder = { Text("Petrol pump, liters, mechanic bill #...") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_notes_input")
                )

                if (hasError) {
                    Text(
                        text = "Please select vehicle and enter valid amount.",
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
                    if (selectedVehicleId == 0L || amt <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(selectedVehicleId, selectedCategory, amt, selectedDate, notes)
                    }
                },
                modifier = Modifier.testTag("save_expense_button")
            ) {
                Text(if (expenseToEdit == null) "Log Expense" else "Save Expense")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

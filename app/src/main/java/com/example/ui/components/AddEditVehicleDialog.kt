package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.example.data.entity.VehicleEntity

@Composable
fun AddEditVehicleDialog(
    vehicleToEdit: VehicleEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, numberPlate: String, type: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(vehicleToEdit?.name ?: "") }
    var numberPlate by remember { mutableStateOf(vehicleToEdit?.numberPlate ?: "") }
    var selectedType by remember { mutableStateOf(vehicleToEdit?.type ?: "Truck") }
    var notes by remember { mutableStateOf(vehicleToEdit?.notes ?: "") }
    var hasError by remember { mutableStateOf(false) }

    val vehicleTypes = listOf("Truck", "Mini Truck", "Bus", "Van", "Car", "Trailer", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (vehicleToEdit == null) "Add New Vehicle" else "Edit Vehicle",
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
                    label = { Text("Vehicle Name / Model *") },
                    placeholder = { Text("e.g. Tata 407, Eicher Pro") },
                    singleLine = true,
                    isError = hasError && name.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_name_input")
                )

                OutlinedTextField(
                    value = numberPlate,
                    onValueChange = {
                        numberPlate = it.uppercase()
                        hasError = false
                    },
                    label = { Text("Number Plate / Registration *") },
                    placeholder = { Text("e.g. MH 12 AB 1234") },
                    singleLine = true,
                    isError = hasError && numberPlate.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_number_plate_input")
                )

                Text(
                    text = "Vehicle Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vehicleTypes.take(4).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vehicleTypes.drop(4).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("Capacity, assigned route, permit expiry...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vehicle_notes_input")
                )

                if (hasError) {
                    Text(
                        text = "Please enter vehicle name and number plate",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || numberPlate.isBlank()) {
                        hasError = true
                    } else {
                        onConfirm(name, numberPlate, selectedType, notes)
                    }
                },
                modifier = Modifier.testTag("save_vehicle_button")
            ) {
                Text(if (vehicleToEdit == null) "Add Vehicle" else "Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

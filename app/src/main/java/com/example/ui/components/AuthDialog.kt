package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.UserRole

@Composable
fun AuthDialog(
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSignIn: (email: String, pass: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSignUpBusiness: (name: String, businessName: String, email: String, pass: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onJoinBusiness: (name: String, joinCode: String, role: UserRole, email: String, pass: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.DRIVER) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Column {
                Text(
                    text = "Cloud Sync & Multi-User",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Sync vehicles, bookings & expenses across all devices in real-time.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null },
                        text = { Text("Sign In", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null },
                        text = { Text("New Business", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; errorMessage = null },
                        text = { Text("Join Team", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (selectedTab != 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_name_input"),
                        singleLine = true
                    )
                }

                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business / Company Name") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_business_name_input"),
                        singleLine = true
                    )
                }

                if (selectedTab == 2) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase() },
                        label = { Text("Business Join Code (e.g. RB-1234)") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_join_code_input"),
                        singleLine = true
                    )

                    Text(
                        text = "Select your role in the company:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedRole = UserRole.MANAGER },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = if (selectedRole == UserRole.MANAGER) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Manager", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedRole = UserRole.ADMIN },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = if (selectedRole == UserRole.ADMIN) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Admin", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedRole = UserRole.DRIVER },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = if (selectedRole == UserRole.DRIVER) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Driver", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    when (selectedTab) {
                        0 -> {
                            onSignIn(email.trim(), password) { success, err ->
                                isLoading = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: "Sign in failed"
                                }
                            }
                        }
                        1 -> {
                            if (name.isBlank() || businessName.isBlank()) {
                                isLoading = false
                                errorMessage = "Please enter your name and business name"
                                return@Button
                            }
                            onSignUpBusiness(name.trim(), businessName.trim(), email.trim(), password) { success, err ->
                                isLoading = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: "Registration failed"
                                }
                            }
                        }
                        2 -> {
                            if (name.isBlank() || joinCode.isBlank()) {
                                isLoading = false
                                errorMessage = "Please enter your name and business join code"
                                return@Button
                            }
                            onJoinBusiness(name.trim(), joinCode.trim(), selectedRole, email.trim(), password) { success, err ->
                                isLoading = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: "Join failed"
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("auth_submit_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = when (selectedTab) {
                        0 -> "Sign In"
                        1 -> "Create Business"
                        2 -> "Join Team"
                        else -> "Continue"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

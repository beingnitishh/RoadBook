package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ProfileDto
import com.example.data.remote.UserRole

@Composable
fun TeamMembersDialog(
    businessName: String,
    joinCode: String,
    currentUserRole: UserRole,
    onDismiss: () -> Unit,
    onFetchMembers: ((List<ProfileDto>) -> Unit) -> Unit,
    onUpdateRole: (userId: String, newRole: UserRole, (Boolean, String?) -> Unit) -> Unit
) {
    var members by remember { mutableStateOf<List<ProfileDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var copiedFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        onFetchMembers { list ->
            members = list
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Team Members",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = businessName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Join Code banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Business Join Code:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = joinCode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(joinCode))
                                copiedFeedback = true
                            },
                            modifier = Modifier.testTag("copy_join_code_btn")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (copiedFeedback) {
                    Text(
                        text = "Join code copied to clipboard! Share it with drivers & managers to connect their phones.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "People in this Business (${members.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (members.isEmpty()) {
                    Text(
                        text = "No team members found yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(members, key = { it.id }) { member ->
                            TeamMemberItem(
                                member = member,
                                canEditRole = currentUserRole == UserRole.ADMIN,
                                onRoleSelected = { newRole ->
                                    onUpdateRole(member.id, newRole) { success, _ ->
                                        if (success) {
                                            members = members.map {
                                                if (it.id == member.id) it.copy(role = newRole) else it
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun TeamMemberItem(
    member: ProfileDto,
    canEditRole: Boolean,
    onRoleSelected: (UserRole) -> Unit
) {
    var roleDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name.ifBlank { "User" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (member.email.isNotBlank()) {
                    Text(
                        text = member.email,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (member.role) {
                        UserRole.ADMIN -> MaterialTheme.colorScheme.primaryContainer
                        UserRole.MANAGER -> MaterialTheme.colorScheme.secondaryContainer
                        UserRole.DRIVER -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier.testTag("member_role_${member.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = member.role.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (member.role) {
                                UserRole.ADMIN -> MaterialTheme.colorScheme.onPrimaryContainer
                                UserRole.MANAGER -> MaterialTheme.colorScheme.onSecondaryContainer
                                UserRole.DRIVER -> MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        if (canEditRole) {
                            IconButton(
                                onClick = { roleDropdownExpanded = true },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Change role",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (canEditRole) {
                    DropdownMenu(
                        expanded = roleDropdownExpanded,
                        onDismissRequest = { roleDropdownExpanded = false }
                    ) {
                        UserRole.values().forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name) },
                                onClick = {
                                    roleDropdownExpanded = false
                                    onRoleSelected(role)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

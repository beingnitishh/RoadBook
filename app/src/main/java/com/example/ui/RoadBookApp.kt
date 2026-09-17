package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity
import com.example.data.repository.SyncStatus
import com.example.ui.components.AddEditBookingDialog
import com.example.ui.components.AddEditDriverDialog
import com.example.ui.components.AddEditExpenseDialog
import com.example.ui.components.AddEditVehicleDialog
import com.example.ui.components.AuthDialog
import com.example.ui.components.LogDriverPaymentDialog
import com.example.ui.components.TeamMembersDialog
import com.example.ui.screens.BookingsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DriverDetailScreen
import com.example.ui.screens.DriversScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VehicleDetailScreen
import com.example.ui.screens.VehiclesScreen
import com.example.ui.viewmodel.RoadBookViewModel

enum class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Filled.SpaceDashboard, Icons.Outlined.SpaceDashboard, "nav_dashboard"),
    VEHICLES("Vehicles", Icons.Filled.LocalShipping, Icons.Outlined.LocalShipping, "nav_vehicles"),
    BOOKINGS("Bookings", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_bookings"),
    EXPENSES("Expenses", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation, "nav_expenses"),
    DRIVERS("Drivers", Icons.Filled.People, Icons.Outlined.People, "nav_drivers")
}

sealed class CurrentScreen {
    object MainNavigation : CurrentScreen()
    data class VehicleDetail(val vehicleId: Long) : CurrentScreen()
    data class DriverDetail(val driverId: Long) : CurrentScreen()
    object Reports : CurrentScreen()
    object Settings : CurrentScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadBookApp(
    viewModel: RoadBookViewModel,
    modifier: Modifier = Modifier
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val driverPayments by viewModel.driverPayments.collectAsStateWithLifecycle()

    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val vehicleStatsList by viewModel.vehicleStatsList.collectAsStateWithLifecycle()
    val driverStatsList by viewModel.driverStatsList.collectAsStateWithLifecycle()
    val recentActivities by viewModel.recentActivities.collectAsStateWithLifecycle()

    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<CurrentScreen>(CurrentScreen.MainNavigation) }
    var selectedNavItem by remember { mutableStateOf(NavigationItem.DASHBOARD) }

    // Dialog state holders
    var vehicleToEdit by remember { mutableStateOf<VehicleEntity?>(null) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }

    var bookingToEdit by remember { mutableStateOf<BookingEntity?>(null) }
    var showAddBookingDialog by remember { mutableStateOf(false) }
    var bookingPreselectedVehicleId by remember { mutableStateOf<Long?>(null) }

    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var expensePreselectedVehicleId by remember { mutableStateOf<Long?>(null) }

    var driverToEdit by remember { mutableStateOf<DriverEntity?>(null) }
    var showAddDriverDialog by remember { mutableStateOf(false) }

    var showLogPaymentDialog by remember { mutableStateOf(false) }
    var paymentPreselectedDriverId by remember { mutableStateOf<Long?>(null) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var authDialogInitialTab by remember { mutableIntStateOf(0) }
    var showTeamMembersDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (currentScreen is CurrentScreen.MainNavigation) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedNavItem) {
                                NavigationItem.DASHBOARD -> "RoadBook"
                                NavigationItem.VEHICLES -> "Vehicles & Fleet"
                                NavigationItem.BOOKINGS -> "Trips & Bookings"
                                NavigationItem.EXPENSES -> "Expenses & Fuel"
                                NavigationItem.DRIVERS -> "Drivers & Payroll"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // Real-time Cloud Sync Chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when (syncStatus) {
                                SyncStatus.SYNCED -> MaterialTheme.colorScheme.primaryContainer
                                SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondaryContainer
                                SyncStatus.OFFLINE -> MaterialTheme.colorScheme.errorContainer
                                SyncStatus.UNCONFIGURED -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .clickable {
                                    if (sessionState.isAuthenticated) {
                                        viewModel.triggerSync()
                                    } else {
                                        authDialogInitialTab = 0
                                        showAuthDialog = true
                                    }
                                }
                                .testTag("topbar_sync_status_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (syncStatus) {
                                        SyncStatus.SYNCED -> Icons.Default.CloudDone
                                        SyncStatus.SYNCING -> Icons.Default.CloudSync
                                        SyncStatus.OFFLINE -> Icons.Default.CloudOff
                                        SyncStatus.UNCONFIGURED -> Icons.Default.Cloud
                                    },
                                    contentDescription = "Sync status",
                                    modifier = Modifier.size(14.dp),
                                    tint = when (syncStatus) {
                                        SyncStatus.SYNCED -> MaterialTheme.colorScheme.onPrimaryContainer
                                        SyncStatus.SYNCING -> MaterialTheme.colorScheme.onSecondaryContainer
                                        SyncStatus.OFFLINE -> MaterialTheme.colorScheme.onErrorContainer
                                        SyncStatus.UNCONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (syncStatus) {
                                        SyncStatus.SYNCED -> if (sessionState.businessJoinCode != null) sessionState.businessJoinCode!! else "Synced"
                                        SyncStatus.SYNCING -> "Syncing"
                                        SyncStatus.OFFLINE -> "Offline"
                                        SyncStatus.UNCONFIGURED -> "Connect"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (syncStatus) {
                                        SyncStatus.SYNCED -> MaterialTheme.colorScheme.onPrimaryContainer
                                        SyncStatus.SYNCING -> MaterialTheme.colorScheme.onSecondaryContainer
                                        SyncStatus.OFFLINE -> MaterialTheme.colorScheme.onErrorContainer
                                        SyncStatus.UNCONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = { currentScreen = CurrentScreen.Reports },
                            modifier = Modifier.testTag("topbar_reports_btn")
                        ) {
                            Icon(
                                Icons.Outlined.Assessment,
                                contentDescription = "Reports & Analytics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { currentScreen = CurrentScreen.Settings },
                            modifier = Modifier.testTag("topbar_settings_btn")
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            } else if (currentScreen is CurrentScreen.Reports) {
                TopAppBar(
                    title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = CurrentScreen.MainNavigation }) {
                            Icon(Icons.Filled.SpaceDashboard, contentDescription = "Back")
                        }
                    }
                )
            } else if (currentScreen is CurrentScreen.Settings) {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = CurrentScreen.MainNavigation }) {
                            Icon(Icons.Filled.SpaceDashboard, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen is CurrentScreen.MainNavigation) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationItem.values().forEach { item ->
                        val isSelected = selectedNavItem == item
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedNavItem = item },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is CurrentScreen.MainNavigation -> {
                    when (selectedNavItem) {
                        NavigationItem.DASHBOARD -> {
                            DashboardScreen(
                                stats = dashboardStats,
                                vehicleStats = vehicleStatsList,
                                recentActivities = recentActivities,
                                selectedPeriod = selectedPeriod,
                                currencySymbol = currencySymbol,
                                onPeriodSelected = { viewModel.setPeriod(it) },
                                onQuickAddBooking = {
                                    bookingToEdit = null
                                    bookingPreselectedVehicleId = null
                                    showAddBookingDialog = true
                                },
                                onQuickAddExpense = {
                                    expenseToEdit = null
                                    expensePreselectedVehicleId = null
                                    showAddExpenseDialog = true
                                },
                                onQuickAddPayment = {
                                    paymentPreselectedDriverId = null
                                    showLogPaymentDialog = true
                                },
                                onVehicleClick = { vId ->
                                    currentScreen = CurrentScreen.VehicleDetail(vId)
                                },
                                onViewAllBookings = {
                                    selectedNavItem = NavigationItem.BOOKINGS
                                },
                                onViewAllExpenses = {
                                    selectedNavItem = NavigationItem.EXPENSES
                                }
                            )
                        }

                        NavigationItem.VEHICLES -> {
                            VehiclesScreen(
                                vehicleStats = vehicleStatsList,
                                currencySymbol = currencySymbol,
                                onVehicleClick = { vId ->
                                    currentScreen = CurrentScreen.VehicleDetail(vId)
                                },
                                onAddVehicleClick = {
                                    vehicleToEdit = null
                                    showAddVehicleDialog = true
                                }
                            )
                        }

                        NavigationItem.BOOKINGS -> {
                            BookingsScreen(
                                bookings = bookings,
                                vehicles = vehicles,
                                drivers = drivers,
                                currencySymbol = currencySymbol,
                                onAddBookingClick = {
                                    bookingToEdit = null
                                    bookingPreselectedVehicleId = null
                                    showAddBookingDialog = true
                                },
                                onEditBookingClick = { b ->
                                    bookingToEdit = b
                                    showAddBookingDialog = true
                                },
                                onDeleteBookingClick = { b ->
                                    viewModel.deleteBooking(b)
                                },
                                onTogglePaymentStatus = { b ->
                                    viewModel.toggleBookingPaymentStatus(b)
                                }
                            )
                        }

                        NavigationItem.EXPENSES -> {
                            ExpensesScreen(
                                expenses = expenses,
                                vehicles = vehicles,
                                currencySymbol = currencySymbol,
                                onAddExpenseClick = {
                                    expenseToEdit = null
                                    expensePreselectedVehicleId = null
                                    showAddExpenseDialog = true
                                },
                                onEditExpenseClick = { e ->
                                    expenseToEdit = e
                                    showAddExpenseDialog = true
                                },
                                onDeleteExpenseClick = { e ->
                                    viewModel.deleteExpense(e)
                                }
                            )
                        }

                        NavigationItem.DRIVERS -> {
                            DriversScreen(
                                driverStatsList = driverStatsList,
                                currencySymbol = currencySymbol,
                                onDriverClick = { dId ->
                                    currentScreen = CurrentScreen.DriverDetail(dId)
                                },
                                onAddDriverClick = {
                                    driverToEdit = null
                                    showAddDriverDialog = true
                                },
                                onLogPaymentClick = { dId ->
                                    paymentPreselectedDriverId = dId
                                    showLogPaymentDialog = true
                                }
                            )
                        }
                    }
                }

                is CurrentScreen.VehicleDetail -> {
                    val vehicle = remember(vehicles, screen.vehicleId) { vehicles.find { it.id == screen.vehicleId } }
                    if (vehicle != null) {
                        val assignedDriver = remember(drivers, vehicle.id) { drivers.find { it.assignedVehicleId == vehicle.id } }
                        val vehicleBookings = remember(bookings, vehicle.id) { bookings.filter { it.vehicleId == vehicle.id } }
                        val vehicleExpenses = remember(expenses, vehicle.id) { expenses.filter { it.vehicleId == vehicle.id } }
                        val vehiclePayments = remember(driverPayments, vehicle.id) { driverPayments.filter { it.vehicleId == vehicle.id } }

                        VehicleDetailScreen(
                            vehicle = vehicle,
                            assignedDriver = assignedDriver,
                            allDrivers = drivers,
                            bookings = vehicleBookings,
                            expenses = vehicleExpenses,
                            driverPayments = vehiclePayments,
                            currencySymbol = currencySymbol,
                            onBackClick = { currentScreen = CurrentScreen.MainNavigation },
                            onEditVehicleClick = {
                                vehicleToEdit = vehicle
                                showAddVehicleDialog = true
                            },
                            onDeleteVehicleClick = {
                                viewModel.deleteVehicle(vehicle)
                                currentScreen = CurrentScreen.MainNavigation
                            },
                            onAssignDriver = { driverId ->
                                viewModel.assignVehicleDriver(vehicle.id, driverId)
                            },
                            onAddBookingForVehicle = {
                                bookingToEdit = null
                                bookingPreselectedVehicleId = vehicle.id
                                showAddBookingDialog = true
                            },
                            onAddExpenseForVehicle = {
                                expenseToEdit = null
                                expensePreselectedVehicleId = vehicle.id
                                showAddExpenseDialog = true
                            },
                            onToggleBookingStatus = { b ->
                                viewModel.toggleBookingPaymentStatus(b)
                            },
                            onEditBookingClick = { b ->
                                bookingToEdit = b
                                showAddBookingDialog = true
                            },
                            onEditExpenseClick = { e ->
                                expenseToEdit = e
                                showAddExpenseDialog = true
                            }
                        )
                    } else {
                        currentScreen = CurrentScreen.MainNavigation
                    }
                }

                is CurrentScreen.DriverDetail -> {
                    val driver = remember(drivers, screen.driverId) { drivers.find { it.id == screen.driverId } }
                    if (driver != null) {
                        val assignedVehicle = remember(vehicles, driver.assignedVehicleId) { vehicles.find { it.id == driver.assignedVehicleId } }
                        val payments = remember(driverPayments, driver.id) { driverPayments.filter { it.driverId == driver.id } }

                        DriverDetailScreen(
                            driver = driver,
                            assignedVehicle = assignedVehicle,
                            payments = payments,
                            currencySymbol = currencySymbol,
                            onBackClick = { currentScreen = CurrentScreen.MainNavigation },
                            onEditDriverClick = {
                                driverToEdit = driver
                                showAddDriverDialog = true
                            },
                            onDeleteDriverClick = {
                                viewModel.deleteDriver(driver)
                                currentScreen = CurrentScreen.MainNavigation
                            },
                            onLogPaymentClick = {
                                paymentPreselectedDriverId = driver.id
                                showLogPaymentDialog = true
                            },
                            onDeletePaymentClick = { p ->
                                viewModel.deleteDriverPayment(p)
                            }
                        )
                    } else {
                        currentScreen = CurrentScreen.MainNavigation
                    }
                }

                is CurrentScreen.Reports -> {
                    ReportsScreen(
                        stats = dashboardStats,
                        vehicleStats = vehicleStatsList,
                        driverStats = driverStatsList,
                        allBookings = bookings,
                        allExpenses = expenses,
                        allPayments = driverPayments,
                        selectedPeriod = selectedPeriod,
                        currencySymbol = currencySymbol,
                        onPeriodSelected = { viewModel.setPeriod(it) }
                    )
                }

                is CurrentScreen.Settings -> {
                    SettingsScreen(
                        currencySymbol = currencySymbol,
                        sessionState = sessionState,
                        syncStatus = syncStatus,
                        onCurrencyChange = { viewModel.setCurrency(it) },
                        onSeedSampleData = { viewModel.seedSampleData() },
                        onClearAllData = { viewModel.clearAllData() },
                        onOpenAuthDialog = { tab ->
                            authDialogInitialTab = tab
                            showAuthDialog = true
                        },
                        onOpenTeamMembersDialog = { showTeamMembersDialog = true },
                        onTriggerSync = { viewModel.triggerSync() },
                        onSignOut = { viewModel.signOut() }
                    )
                }
            }
        }
    }

    // Vehicle Dialog
    if (showAddVehicleDialog) {
        AddEditVehicleDialog(
            vehicleToEdit = vehicleToEdit,
            onDismiss = { showAddVehicleDialog = false },
            onConfirm = { name, numberPlate, type, notes ->
                if (vehicleToEdit == null) {
                    viewModel.addVehicle(name, numberPlate, type, notes)
                } else {
                    viewModel.updateVehicle(vehicleToEdit!!.copy(name = name, numberPlate = numberPlate, type = type, notes = notes))
                }
                showAddVehicleDialog = false
            }
        )
    }

    // Booking Dialog
    if (showAddBookingDialog) {
        AddEditBookingDialog(
            bookingToEdit = bookingToEdit,
            vehicles = vehicles,
            drivers = drivers,
            preselectedVehicleId = bookingPreselectedVehicleId,
            currencySymbol = currencySymbol,
            onDismiss = { showAddBookingDialog = false },
            onConfirm = { vehicleId, driverId, date, customerRoute, amount, paymentStatus, notes ->
                if (bookingToEdit == null) {
                    viewModel.addBooking(vehicleId, driverId, date, customerRoute, amount, paymentStatus, notes)
                } else {
                    viewModel.updateBooking(bookingToEdit!!.copy(
                        vehicleId = vehicleId,
                        driverId = driverId,
                        date = date,
                        customerRoute = customerRoute,
                        amount = amount,
                        paymentStatus = paymentStatus,
                        notes = notes
                    ))
                }
                showAddBookingDialog = false
            }
        )
    }

    // Expense Dialog
    if (showAddExpenseDialog) {
        AddEditExpenseDialog(
            expenseToEdit = expenseToEdit,
            vehicles = vehicles,
            preselectedVehicleId = expensePreselectedVehicleId,
            currencySymbol = currencySymbol,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { vehicleId, category, amount, date, notes ->
                if (expenseToEdit == null) {
                    viewModel.addExpense(vehicleId, category, amount, date, notes)
                } else {
                    viewModel.updateExpense(expenseToEdit!!.copy(
                        vehicleId = vehicleId,
                        category = category,
                        amount = amount,
                        date = date,
                        notes = notes
                    ))
                }
                showAddExpenseDialog = false
            }
        )
    }

    // Driver Dialog
    if (showAddDriverDialog) {
        AddEditDriverDialog(
            driverToEdit = driverToEdit,
            vehicles = vehicles,
            currencySymbol = currencySymbol,
            onDismiss = { showAddDriverDialog = false },
            onConfirm = { name, phone, paymentType, rate, assignedVehicleId, notes ->
                if (driverToEdit == null) {
                    viewModel.addDriver(name, phone, paymentType, rate, assignedVehicleId, notes)
                } else {
                    viewModel.updateDriver(driverToEdit!!.copy(
                        name = name,
                        phone = phone,
                        paymentType = paymentType,
                        rate = rate,
                        assignedVehicleId = assignedVehicleId,
                        notes = notes
                    ))
                }
                showAddDriverDialog = false
            }
        )
    }

    // Payment Dialog
    if (showLogPaymentDialog) {
        LogDriverPaymentDialog(
            drivers = drivers,
            preselectedDriverId = paymentPreselectedDriverId,
            currencySymbol = currencySymbol,
            onDismiss = { showLogPaymentDialog = false },
            onConfirm = { driverId, vehicleId, amount, date, notes ->
                viewModel.addDriverPayment(driverId, vehicleId, amount, date, notes)
                showLogPaymentDialog = false
            }
        )
    }

    // Cloud Auth & Business Dialog
    if (showAuthDialog) {
        AuthDialog(
            initialTab = authDialogInitialTab,
            onDismiss = { showAuthDialog = false },
            onSignIn = { email, pass, onResult ->
                viewModel.signIn(email, pass, onResult)
            },
            onSignUpBusiness = { name, businessName, email, pass, onResult ->
                viewModel.signUpAndCreateBusiness(name, businessName, email, pass, onResult)
            },
            onJoinBusiness = { name, joinCode, role, email, pass, onResult ->
                viewModel.signUpAndJoinBusiness(name, joinCode, role, email, pass, onResult)
            }
        )
    }

    // Team Members Dialog
    if (showTeamMembersDialog) {
        TeamMembersDialog(
            businessName = sessionState.businessName ?: "My Business",
            joinCode = sessionState.businessJoinCode ?: "",
            currentUserRole = sessionState.role,
            onDismiss = { showTeamMembersDialog = false },
            onFetchMembers = { callback ->
                viewModel.fetchTeamMembers(callback)
            },
            onUpdateRole = { userId, newRole, callback ->
                viewModel.updateMemberRole(userId, newRole, callback)
            }
        )
    }
}

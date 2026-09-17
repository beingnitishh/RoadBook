package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RoadBookDatabase
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.RoadBookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class PeriodFilter(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_20_DAYS("Last 20 Days"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

data class DashboardStats(
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalDriverPayments: Double = 0.0,
    val netProfit: Double = 0.0,
    val pendingReceivables: Double = 0.0,
    val paidIncome: Double = 0.0,
    val bookingsCount: Int = 0,
    val expensesCount: Int = 0
)

data class VehicleStats(
    val vehicle: VehicleEntity,
    val assignedDriver: DriverEntity?,
    val bookingsCount: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalDriverCost: Double,
    val netProfit: Double
)

data class DriverStats(
    val driver: DriverEntity,
    val assignedVehicle: VehicleEntity?,
    val totalPaid: Double,
    val tripsCount: Int,
    val paymentsCount: Int
)

sealed class ActivityItem(val timestamp: Long, val vehicleId: Long?) {
    data class BookingActivity(val booking: BookingEntity, val vehicleName: String, val driverName: String? = null) :
        ActivityItem(booking.date, booking.vehicleId)

    data class ExpenseActivity(val expense: ExpenseEntity, val vehicleName: String) :
        ActivityItem(expense.date, expense.vehicleId)

    data class DriverPaymentActivity(val payment: DriverPaymentEntity, val driverName: String) :
        ActivityItem(payment.date, payment.vehicleId)
}

class RoadBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RoadBookRepository
    private val prefs = AppPreferences(application)

    init {
        val db = RoadBookDatabase.getDatabase(application)
        repository = RoadBookRepository(
            vehicleDao = db.vehicleDao(),
            driverDao = db.driverDao(),
            bookingDao = db.bookingDao(),
            expenseDao = db.expenseDao(),
            driverPaymentDao = db.driverPaymentDao(),
            preferences = prefs,
            networkMonitor = com.example.util.NetworkMonitor(application)
        )

        // Seed demo data once automatically on first launch if not seeded yet and no cloud user
        viewModelScope.launch {
            if (!prefs.isInitialSeeded && prefs.accessToken == null) {
                repository.seedSampleData()
                prefs.isInitialSeeded = true
            }
        }
    }

    val syncStatus: StateFlow<com.example.data.repository.SyncStatus> = repository.syncStatus
    val sessionState: StateFlow<com.example.data.repository.UserSessionState> = repository.sessionState
    val currentRole: StateFlow<com.example.data.remote.UserRole> = combine(repository.sessionState) { it[0].role }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.UserRole.DRIVER)

    // Role-based permissions
    fun canManageVehicles(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role in listOf(com.example.data.remote.UserRole.ADMIN, com.example.data.remote.UserRole.MANAGER)
    fun canDeleteVehicles(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role == com.example.data.remote.UserRole.ADMIN
    fun canManageDrivers(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role in listOf(com.example.data.remote.UserRole.ADMIN, com.example.data.remote.UserRole.MANAGER)
    fun canDeleteDrivers(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role == com.example.data.remote.UserRole.ADMIN
    fun canManageBookings(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role in listOf(com.example.data.remote.UserRole.ADMIN, com.example.data.remote.UserRole.MANAGER, com.example.data.remote.UserRole.DRIVER)
    fun canDeleteBookings(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role == com.example.data.remote.UserRole.ADMIN
    fun canManageExpenses(): Boolean = true // Drivers, Managers, Admins can add operational expenses
    fun canDeleteExpenses(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role in listOf(com.example.data.remote.UserRole.ADMIN, com.example.data.remote.UserRole.MANAGER)
    fun canManageDriverPayments(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role == com.example.data.remote.UserRole.ADMIN
    fun canManageTeam(): Boolean = !sessionState.value.isAuthenticated || sessionState.value.role == com.example.data.remote.UserRole.ADMIN

    // Auth & Business Methods
    fun signUp(email: String, pass: String, name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.signUp(email, pass, name)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.signIn(email, pass)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    fun createBusiness(name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.createBusiness(name)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to create business")
            }
        }
    }

    fun joinBusiness(joinCode: String, role: com.example.data.remote.UserRole, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.joinBusinessByCode(joinCode, role)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to join business")
            }
        }
    }

    fun fetchTeamMembers(onResult: (List<com.example.data.remote.ProfileDto>) -> Unit) {
        viewModelScope.launch {
            val res = repository.fetchTeamMembers()
            onResult(res.getOrDefault(emptyList()))
        }
    }

    fun updateMemberRole(userId: String, newRole: com.example.data.remote.UserRole, onResult: (Boolean, String?) -> Unit) {
        if (!canManageTeam()) {
            onResult(false, "Only admins can change team member roles")
            return
        }
        if (userId == sessionState.value.userId) {
            onResult(false, "Users cannot change their own role")
            return
        }
        viewModelScope.launch {
            val res = repository.updateMemberRole(userId, newRole)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to update role")
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            repository.triggerSync()
        }
    }

    fun setCustomSupabaseCredentials(url: String, key: String) {
        repository.setCustomSupabaseCredentials(url, key)
    }

    fun getSupabaseUrl(): String = repository.getSupabaseUrl()
    fun getSupabaseAnonKey(): String = repository.getSupabaseAnonKey()
    fun isSupabaseConfigured(): Boolean = repository.isSupabaseConfigured()
    fun saveSupabaseConfig(url: String, key: String) = repository.setCustomSupabaseCredentials(url, key)

    fun signUpAndCreateBusiness(name: String, businessName: String, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val signUpRes = repository.signUp(email, pass, name)
            if (signUpRes.isFailure) {
                // If account exists, attempt sign in
                val signInRes = repository.signIn(email, pass)
                if (signInRes.isFailure) {
                    onResult(false, signUpRes.exceptionOrNull()?.message ?: "Registration failed")
                    return@launch
                }
            }
            val bRes = repository.createBusiness(businessName)
            if (bRes.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, bRes.exceptionOrNull()?.message ?: "Failed to create business")
            }
        }
    }

    fun signUpAndJoinBusiness(name: String, joinCode: String, role: com.example.data.remote.UserRole, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val signUpRes = repository.signUp(email, pass, name)
            if (signUpRes.isFailure) {
                val signInRes = repository.signIn(email, pass)
                if (signInRes.isFailure) {
                    onResult(false, signUpRes.exceptionOrNull()?.message ?: "Sign up/in failed")
                    return@launch
                }
            }
            val joinRes = repository.joinBusinessByCode(joinCode, role)
            if (joinRes.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, joinRes.exceptionOrNull()?.message ?: "Failed to join business")
            }
        }
    }

    val vehicles: StateFlow<List<VehicleEntity>> = repository.allVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val drivers: StateFlow<List<DriverEntity>> = repository.allDrivers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookings: StateFlow<List<BookingEntity>> = repository.allBookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val driverPayments: StateFlow<List<DriverPaymentEntity>> = repository.allDriverPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.LAST_30_DAYS)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    private val _currencySymbol = MutableStateFlow(prefs.currencySymbol)
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    fun setPeriodFilter(period: PeriodFilter) {
        _selectedPeriod.value = period
    }

    fun setPeriod(period: PeriodFilter) = setPeriodFilter(period)

    fun setCurrency(symbol: String) {
        prefs.currencySymbol = symbol
        _currencySymbol.value = symbol
    }

    private fun getPeriodStartTime(period: PeriodFilter): Long {
        val cal = Calendar.getInstance()
        return when (period) {
            PeriodFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            PeriodFilter.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            PeriodFilter.LAST_20_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -20)
                cal.timeInMillis
            }
            PeriodFilter.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            PeriodFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            PeriodFilter.ALL_TIME -> 0L
        }
    }

    // Dashboard Combined Stats
    val dashboardStats: StateFlow<DashboardStats> = combine(
        bookings,
        expenses,
        driverPayments,
        _selectedPeriod
    ) { bList, eList, pList, period ->
        val startTime = getPeriodStartTime(period)
        var totalIncome = 0.0
        var paidIncome = 0.0
        var pendingReceivables = 0.0
        var bookingsCount = 0

        for (b in bList) {
            if (b.date >= startTime) {
                totalIncome += b.amount
                bookingsCount++
                if (b.paymentStatus.equals("PAID", ignoreCase = true)) {
                    paidIncome += b.amount
                } else if (b.paymentStatus.equals("PENDING", ignoreCase = true)) {
                    pendingReceivables += b.amount
                }
            }
        }

        var totalExpenses = 0.0
        var expensesCount = 0
        for (e in eList) {
            if (e.date >= startTime) {
                totalExpenses += e.amount
                expensesCount++
            }
        }

        var totalDriverPayments = 0.0
        for (p in pList) {
            if (p.date >= startTime) {
                totalDriverPayments += p.amount
            }
        }

        val netProfit = totalIncome - (totalExpenses + totalDriverPayments)

        DashboardStats(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            totalDriverPayments = totalDriverPayments,
            netProfit = netProfit,
            pendingReceivables = pendingReceivables,
            paidIncome = paidIncome,
            bookingsCount = bookingsCount,
            expensesCount = expensesCount
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // Per-Vehicle Performance
    val vehicleStatsList: StateFlow<List<VehicleStats>> = combine(
        vehicles,
        drivers,
        bookings,
        expenses,
        driverPayments,
        _selectedPeriod
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val vList = args[0] as List<VehicleEntity>
        @Suppress("UNCHECKED_CAST")
        val dList = args[1] as List<DriverEntity>
        @Suppress("UNCHECKED_CAST")
        val bList = args[2] as List<BookingEntity>
        @Suppress("UNCHECKED_CAST")
        val eList = args[3] as List<ExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val pList = args[4] as List<DriverPaymentEntity>
        val period = args[5] as PeriodFilter

        val startTime = getPeriodStartTime(period)
        val driverByVehicleId = dList.filter { it.assignedVehicleId != null }.associateBy { it.assignedVehicleId!! }

        // Pre-group by vehicleId for O(1) access
        val bookingsByVehicle = bList.filter { it.date >= startTime }.groupBy { it.vehicleId }
        val expensesByVehicle = eList.filter { it.date >= startTime }.groupBy { it.vehicleId }
        val paymentsByVehicle = pList.filter { it.date >= startTime && it.vehicleId != null }.groupBy { it.vehicleId!! }
        val paymentsByDriver = pList.filter { it.date >= startTime }.groupBy { it.driverId }

        vList.map { vehicle ->
            val assignedDriver = driverByVehicleId[vehicle.id]
            val vBookings = bookingsByVehicle[vehicle.id] ?: emptyList()
            val vExpenses = expensesByVehicle[vehicle.id] ?: emptyList()

            // Sum payments either tagged to vehicle or assigned driver
            val directPayments = paymentsByVehicle[vehicle.id] ?: emptyList()
            val driverPayments = if (assignedDriver != null) {
                (paymentsByDriver[assignedDriver.id] ?: emptyList()).filter { it.vehicleId == null }
            } else emptyList()

            val income = vBookings.sumOf { it.amount }
            val expenseTotal = vExpenses.sumOf { it.amount }
            val driverCost = directPayments.sumOf { it.amount } + driverPayments.sumOf { it.amount }
            val profit = income - (expenseTotal + driverCost)

            VehicleStats(
                vehicle = vehicle,
                assignedDriver = assignedDriver,
                bookingsCount = vBookings.size,
                totalIncome = income,
                totalExpenses = expenseTotal,
                totalDriverCost = driverCost,
                netProfit = profit
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Per-Driver Performance
    val driverStatsList: StateFlow<List<DriverStats>> = combine(
        drivers,
        vehicles,
        bookings,
        driverPayments
    ) { dList, vList, bList, pList ->
        val vMap = vList.associateBy { it.id }
        val pMap = pList.groupBy { it.driverId }
        val bByDriverMap = bList.groupBy { it.driverId }

        dList.map { driver ->
            val assignedVehicle = driver.assignedVehicleId?.let { vMap[it] }
            val payments = pMap[driver.id] ?: emptyList()
            val totalPaid = payments.sumOf { it.amount }

            // Direct bookings where this driver was chosen, plus legacy/unassigned-driver trips for their assigned truck
            val directTrips = bByDriverMap[driver.id]?.size ?: 0
            val fallbackTrips = if (driver.assignedVehicleId != null) {
                bList.count { it.driverId == null && it.vehicleId == driver.assignedVehicleId }
            } else 0
            val tripsCount = directTrips + fallbackTrips

            DriverStats(
                driver = driver,
                assignedVehicle = assignedVehicle,
                totalPaid = totalPaid,
                tripsCount = tripsCount,
                paymentsCount = payments.size
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recent Combined Activity (Bookings, Expenses, Driver Payments)
    val recentActivities: StateFlow<List<ActivityItem>> = combine(
        bookings,
        expenses,
        driverPayments,
        vehicles,
        drivers
    ) { bList, eList, pList, vList, dList ->
        val vehicleMap = vList.associate { it.id to it.name }
        val driverMap = dList.associate { it.id to it.name }

        val bookingItems = bList.map {
            val dName = it.driverId?.let { dId -> driverMap[dId] }
            ActivityItem.BookingActivity(
                booking = it,
                vehicleName = vehicleMap[it.vehicleId] ?: "Unknown Vehicle",
                driverName = dName
            )
        }
        val expenseItems = eList.map {
            ActivityItem.ExpenseActivity(
                expense = it,
                vehicleName = vehicleMap[it.vehicleId] ?: "Unknown Vehicle"
            )
        }
        val paymentItems = pList.map {
            ActivityItem.DriverPaymentActivity(
                payment = it,
                driverName = driverMap[it.driverId] ?: "Driver"
            )
        }

        (bookingItems + expenseItems + paymentItems)
            .sortedByDescending { it.timestamp }
            .take(25)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // CRUD Actions - Vehicles
    fun addVehicle(name: String, numberPlate: String, type: String, notes: String = "") {
        viewModelScope.launch {
            repository.insertVehicle(
                VehicleEntity(
                    name = name.trim(),
                    numberPlate = numberPlate.trim().uppercase(),
                    type = type.trim(),
                    notes = notes.trim()
                )
            )
        }
    }

    fun updateVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            repository.updateVehicle(vehicle)
        }
    }

    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            // Also unassign this vehicle from any driver
            drivers.value.filter { it.assignedVehicleId == vehicle.id }.forEach { driver ->
                repository.updateDriver(driver.copy(assignedVehicleId = null))
            }
            repository.deleteVehicle(vehicle)
        }
    }

    // CRUD Actions - Drivers
    fun addDriver(name: String, phone: String, paymentType: String, rate: Double, assignedVehicleId: Long?, notes: String = "") {
        viewModelScope.launch {
            // If vehicle is being assigned to this new driver, unassign it from any previous driver
            if (assignedVehicleId != null) {
                drivers.value.filter { it.assignedVehicleId == assignedVehicleId }.forEach { other ->
                    repository.updateDriver(other.copy(assignedVehicleId = null))
                }
            }
            repository.insertDriver(
                DriverEntity(
                    name = name.trim(),
                    phone = phone.trim(),
                    paymentType = paymentType,
                    rate = rate,
                    assignedVehicleId = assignedVehicleId,
                    notes = notes.trim()
                )
            )
        }
    }

    fun updateDriver(driver: DriverEntity) {
        viewModelScope.launch {
            // If vehicle is being assigned, ensure no other driver also has it assigned
            if (driver.assignedVehicleId != null) {
                drivers.value.filter { it.assignedVehicleId == driver.assignedVehicleId && it.id != driver.id }.forEach { other ->
                    repository.updateDriver(other.copy(assignedVehicleId = null))
                }
            }
            repository.updateDriver(driver)
        }
    }

    fun assignVehicleDriver(vehicleId: Long, driverId: Long?) {
        viewModelScope.launch {
            val allDrivers = drivers.value
            // Unassign this vehicle from any driver currently assigned to it
            allDrivers.filter { it.assignedVehicleId == vehicleId && it.id != driverId }.forEach { other ->
                repository.updateDriver(other.copy(assignedVehicleId = null))
            }
            if (driverId != null) {
                val targetDriver = allDrivers.find { it.id == driverId }
                if (targetDriver != null) {
                    repository.updateDriver(targetDriver.copy(assignedVehicleId = vehicleId))
                }
            }
        }
    }

    fun deleteDriver(driver: DriverEntity) {
        viewModelScope.launch {
            repository.deleteDriver(driver)
        }
    }

    // CRUD Actions - Bookings
    fun addBooking(
        vehicleId: Long,
        driverId: Long?,
        date: Long,
        customerRoute: String,
        amount: Double,
        paymentStatus: String,
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.insertBooking(
                BookingEntity(
                    vehicleId = vehicleId,
                    driverId = driverId,
                    date = date,
                    customerRoute = customerRoute.trim(),
                    amount = amount,
                    paymentStatus = paymentStatus,
                    notes = notes.trim()
                )
            )
        }
    }

    fun updateBooking(booking: BookingEntity) {
        viewModelScope.launch {
            repository.updateBooking(booking)
        }
    }

    fun toggleBookingPaymentStatus(booking: BookingEntity) {
        viewModelScope.launch {
            val nextStatus = if (booking.paymentStatus.equals("PAID", ignoreCase = true)) "PENDING" else "PAID"
            repository.updateBooking(booking.copy(paymentStatus = nextStatus))
        }
    }

    fun deleteBooking(booking: BookingEntity) {
        viewModelScope.launch {
            repository.deleteBooking(booking)
        }
    }

    // CRUD Actions - Expenses
    fun addExpense(vehicleId: Long, category: String, amount: Double, date: Long, notes: String = "") {
        viewModelScope.launch {
            repository.insertExpense(
                ExpenseEntity(
                    vehicleId = vehicleId,
                    category = category.trim(),
                    amount = amount,
                    date = date,
                    notes = notes.trim()
                )
            )
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // CRUD Actions - Driver Payments
    fun addDriverPayment(driverId: Long, vehicleId: Long?, amount: Double, date: Long, notes: String = "") {
        viewModelScope.launch {
            repository.insertDriverPayment(
                DriverPaymentEntity(
                    driverId = driverId,
                    vehicleId = vehicleId,
                    amount = amount,
                    date = date,
                    notes = notes.trim()
                )
            )
        }
    }

    fun updateDriverPayment(payment: DriverPaymentEntity) {
        viewModelScope.launch {
            repository.updateDriverPayment(payment)
        }
    }

    fun deleteDriverPayment(payment: DriverPaymentEntity) {
        viewModelScope.launch {
            repository.deleteDriverPayment(payment)
        }
    }

    // Seed & Reset
    fun seedDemoData() {
        viewModelScope.launch {
            repository.seedSampleData()
        }
    }

    fun seedSampleData() = seedDemoData()

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    // Export CSV representation
    fun generateCsvExport(): String {
        val bList = bookings.value
        val eList = expenses.value
        val pList = driverPayments.value
        val vMap = vehicles.value.associate { it.id to it.name }
        val dMap = drivers.value.associate { it.id to it.name }

        val sb = StringBuilder()
        sb.append("--- ROADBOOK EXPORT REPORT ---\n\n")

        sb.append("BOOKINGS:\n")
        sb.append("ID,Date,Vehicle,Customer/Route,Amount,Status,Notes\n")
        for (b in bList) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(b.date))
            val vName = vMap[b.vehicleId] ?: "Unknown"
            sb.append("${b.id},\"$dateStr\",\"$vName\",\"${b.customerRoute}\",${b.amount},${b.paymentStatus},\"${b.notes}\"\n")
        }

        sb.append("\nEXPENSES:\n")
        sb.append("ID,Date,Vehicle,Category,Amount,Notes\n")
        for (e in eList) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(e.date))
            val vName = vMap[e.vehicleId] ?: "Unknown"
            sb.append("${e.id},\"$dateStr\",\"$vName\",\"${e.category}\",${e.amount},\"${e.notes}\"\n")
        }

        sb.append("\nDRIVER PAYMENTS:\n")
        sb.append("ID,Date,Driver,Amount,Notes\n")
        for (p in pList) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(p.date))
            val dName = dMap[p.driverId] ?: "Unknown"
            sb.append("${p.id},\"$dateStr\",\"$dName\",${p.amount},\"${p.notes}\"\n")
        }

        return sb.toString()
    }
}

class RoadBookViewModelFactory(
    private val application: Application
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoadBookViewModel::class.java)) {
            return RoadBookViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


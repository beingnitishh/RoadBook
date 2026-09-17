package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.dao.BookingDao
import com.example.data.dao.DriverDao
import com.example.data.dao.DriverPaymentDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.VehicleDao
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.DriverPaymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.VehicleEntity
import com.example.data.preferences.AppPreferences
import com.example.data.remote.AuthSession
import com.example.data.remote.BookingDto
import com.example.data.remote.BusinessDto
import com.example.data.remote.DriverDto
import com.example.data.remote.DriverPaymentDto
import com.example.data.remote.ExpenseDto
import com.example.data.remote.ProfileDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseRealtimeManager
import com.example.data.remote.UserRole
import com.example.data.remote.VehicleDto
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    UNCONFIGURED
}

data class UserSessionState(
    val isAuthenticated: Boolean = false,
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val role: UserRole = UserRole.DRIVER,
    val businessId: String? = null,
    val businessName: String? = null,
    val businessJoinCode: String? = null
)

class RoadBookRepository(
    private val vehicleDao: VehicleDao,
    private val driverDao: DriverDao,
    private val bookingDao: BookingDao,
    private val expenseDao: ExpenseDao,
    private val driverPaymentDao: DriverPaymentDao,
    val preferences: AppPreferences,
    private val networkMonitor: NetworkMonitor
) {
    private val tag = "RoadBookRepo"
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val supabaseClient = SupabaseClient(
        defaultBaseUrl = try {
            val url = BuildConfig.SUPABASE_URL
            if (url.isNullOrBlank()) "https://oxupecemuytlkzqhdzpt.supabase.co" else url
        } catch (e: Exception) {
            "https://oxupecemuytlkzqhdzpt.supabase.co"
        },
        defaultApiKey = try {
            val key = BuildConfig.SUPABASE_ANON_KEY
            if (key.isNullOrBlank()) "sb_publishable_6SHJgCzplKmPnYoIz7Kv0w_tP0d4_g_" else key
        } catch (e: Exception) {
            "sb_publishable_6SHJgCzplKmPnYoIz7Kv0w_tP0d4_g_"
        }
    ).apply {
        customBaseUrl = preferences.customSupabaseUrl
        customApiKey = preferences.customSupabaseKey
    }

    private val _syncStatus = MutableStateFlow(
        if (!supabaseClient.isConfigured()) SyncStatus.UNCONFIGURED else SyncStatus.SYNCED
    )
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _sessionState = MutableStateFlow(
        UserSessionState(
            isAuthenticated = preferences.accessToken != null,
            userId = preferences.userId.orEmpty(),
            email = preferences.userEmail.orEmpty(),
            name = preferences.userName.orEmpty(),
            role = UserRole.fromString(preferences.userRole),
            businessId = preferences.businessId,
            businessName = preferences.businessName,
            businessJoinCode = preferences.businessJoinCode
        )
    )
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    private var realtimeManager: SupabaseRealtimeManager? = null

    init {
        // Monitor network changes to trigger sync
        repoScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (!online) {
                    _syncStatus.value = SyncStatus.OFFLINE
                } else {
                    if (supabaseClient.isConfigured() && _sessionState.value.isAuthenticated) {
                        triggerSync()
                        startRealtimeListener()
                    } else if (supabaseClient.isConfigured()) {
                        _syncStatus.value = SyncStatus.SYNCED
                    } else {
                        _syncStatus.value = SyncStatus.UNCONFIGURED
                    }
                }
            }
        }

        // Restore session if token exists
        if (preferences.accessToken != null && supabaseClient.isConfigured()) {
            repoScope.launch {
                refreshProfileAndBusiness()
                startRealtimeListener()
                triggerSync()
            }
        }
    }

    // ========================================================================
    // LOCAL ROOM EXPOSURES (Offline-First Single Source of Truth)
    // ========================================================================

    val allVehicles: Flow<List<VehicleEntity>> = vehicleDao.getAllVehicles()
    fun getVehicleById(id: Long): Flow<VehicleEntity?> = vehicleDao.getVehicleById(id)

    val allDrivers: Flow<List<DriverEntity>> = driverDao.getAllDrivers()
    fun getDriverById(id: Long): Flow<DriverEntity?> = driverDao.getDriverById(id)
    fun getDriverByVehicleId(vehicleId: Long): Flow<DriverEntity?> = driverDao.getDriverByVehicleId(vehicleId)

    val allBookings: Flow<List<BookingEntity>> = bookingDao.getAllBookings()
    fun getBookingsForVehicle(vehicleId: Long): Flow<List<BookingEntity>> = bookingDao.getBookingsForVehicle(vehicleId)
    fun getBookingsSince(sinceDate: Long): Flow<List<BookingEntity>> = bookingDao.getBookingsSince(sinceDate)

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    fun getExpensesForVehicle(vehicleId: Long): Flow<List<ExpenseEntity>> = expenseDao.getExpensesForVehicle(vehicleId)

    val allDriverPayments: Flow<List<DriverPaymentEntity>> = driverPaymentDao.getAllDriverPayments()
    fun getPaymentsForDriver(driverId: Long): Flow<List<DriverPaymentEntity>> = driverPaymentDao.getPaymentsForDriver(driverId)

    // ========================================================================
    // MUTATIONS (Room First + Remote Sync)
    // ========================================================================

    suspend fun insertVehicle(vehicle: VehicleEntity): Long = withContext(Dispatchers.IO) {
        val remoteId = if (vehicle.remoteId.isBlank()) UUID.randomUUID().toString() else vehicle.remoteId
        val bId = vehicle.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val toInsert = vehicle.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        val id = vehicleDao.insertVehicle(toInsert)
        pushVehicleToRemote(toInsert.copy(id = id))
        id
    }

    suspend fun updateVehicle(vehicle: VehicleEntity) = withContext(Dispatchers.IO) {
        val remoteId = if (vehicle.remoteId.isBlank()) UUID.randomUUID().toString() else vehicle.remoteId
        val bId = vehicle.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val updated = vehicle.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        vehicleDao.updateVehicle(updated)
        pushVehicleToRemote(updated)
    }

    suspend fun deleteVehicle(vehicle: VehicleEntity) = withContext(Dispatchers.IO) {
        bookingDao.deleteBookingsForVehicle(vehicle.id)
        expenseDao.deleteExpensesForVehicle(vehicle.id)
        vehicleDao.deleteVehicle(vehicle)
        if (vehicle.remoteId.isNotBlank() && _sessionState.value.businessId != null) {
            val token = preferences.accessToken
            if (token != null && networkMonitor.isOnline.value) {
                supabaseClient.deleteVehicle(vehicle.remoteId, token)
            }
        }
    }

    suspend fun insertDriver(driver: DriverEntity): Long = withContext(Dispatchers.IO) {
        val remoteId = if (driver.remoteId.isBlank()) UUID.randomUUID().toString() else driver.remoteId
        val bId = driver.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val toInsert = driver.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        val id = driverDao.insertDriver(toInsert)
        pushDriverToRemote(toInsert.copy(id = id))
        id
    }

    suspend fun updateDriver(driver: DriverEntity) = withContext(Dispatchers.IO) {
        val remoteId = if (driver.remoteId.isBlank()) UUID.randomUUID().toString() else driver.remoteId
        val bId = driver.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val updated = driver.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        driverDao.updateDriver(updated)
        pushDriverToRemote(updated)
    }

    suspend fun deleteDriver(driver: DriverEntity) = withContext(Dispatchers.IO) {
        driverPaymentDao.deletePaymentsForDriver(driver.id)
        driverDao.deleteDriver(driver)
        if (driver.remoteId.isNotBlank() && _sessionState.value.businessId != null) {
            val token = preferences.accessToken
            if (token != null && networkMonitor.isOnline.value) {
                supabaseClient.deleteDriver(driver.remoteId, token)
            }
        }
    }

    suspend fun insertBooking(booking: BookingEntity): Long = withContext(Dispatchers.IO) {
        val remoteId = if (booking.remoteId.isBlank()) UUID.randomUUID().toString() else booking.remoteId
        val bId = booking.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val toInsert = booking.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        val id = bookingDao.insertBooking(toInsert)
        pushBookingToRemote(toInsert.copy(id = id))
        id
    }

    suspend fun updateBooking(booking: BookingEntity) = withContext(Dispatchers.IO) {
        val remoteId = if (booking.remoteId.isBlank()) UUID.randomUUID().toString() else booking.remoteId
        val bId = booking.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val updated = booking.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        bookingDao.updateBooking(updated)
        pushBookingToRemote(updated)
    }

    suspend fun deleteBooking(booking: BookingEntity) = withContext(Dispatchers.IO) {
        bookingDao.deleteBooking(booking)
        if (booking.remoteId.isNotBlank() && _sessionState.value.businessId != null) {
            val token = preferences.accessToken
            if (token != null && networkMonitor.isOnline.value) {
                supabaseClient.deleteBooking(booking.remoteId, token)
            }
        }
    }

    suspend fun insertExpense(expense: ExpenseEntity): Long = withContext(Dispatchers.IO) {
        val remoteId = if (expense.remoteId.isBlank()) UUID.randomUUID().toString() else expense.remoteId
        val bId = expense.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val toInsert = expense.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        val id = expenseDao.insertExpense(toInsert)
        pushExpenseToRemote(toInsert.copy(id = id))
        id
    }

    suspend fun updateExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        val remoteId = if (expense.remoteId.isBlank()) UUID.randomUUID().toString() else expense.remoteId
        val bId = expense.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val updated = expense.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        expenseDao.updateExpense(updated)
        pushExpenseToRemote(updated)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
        if (expense.remoteId.isNotBlank() && _sessionState.value.businessId != null) {
            val token = preferences.accessToken
            if (token != null && networkMonitor.isOnline.value) {
                supabaseClient.deleteExpense(expense.remoteId, token)
            }
        }
    }

    suspend fun insertDriverPayment(payment: DriverPaymentEntity): Long = withContext(Dispatchers.IO) {
        val remoteId = if (payment.remoteId.isBlank()) UUID.randomUUID().toString() else payment.remoteId
        val bId = payment.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val toInsert = payment.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        val id = driverPaymentDao.insertPayment(toInsert)
        pushDriverPaymentToRemote(toInsert.copy(id = id))
        id
    }

    suspend fun updateDriverPayment(payment: DriverPaymentEntity) = withContext(Dispatchers.IO) {
        val remoteId = if (payment.remoteId.isBlank()) UUID.randomUUID().toString() else payment.remoteId
        val bId = payment.businessId.ifBlank { _sessionState.value.businessId.orEmpty() }
        val updated = payment.copy(remoteId = remoteId, businessId = bId, isSynced = false)
        driverPaymentDao.updatePayment(updated)
        pushDriverPaymentToRemote(updated)
    }

    suspend fun deleteDriverPayment(payment: DriverPaymentEntity) = withContext(Dispatchers.IO) {
        driverPaymentDao.deletePayment(payment)
        if (payment.remoteId.isNotBlank() && _sessionState.value.businessId != null) {
            val token = preferences.accessToken
            if (token != null && networkMonitor.isOnline.value) {
                supabaseClient.deleteDriverPayment(payment.remoteId, token)
            }
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        driverPaymentDao.deleteAllDriverPayments()
        expenseDao.deleteAllExpenses()
        bookingDao.deleteAllBookings()
        driverDao.deleteAllDrivers()
        vehicleDao.deleteAllVehicles()
    }

    // ========================================================================
    // REMOTE PUSH HELPERS
    // ========================================================================

    private suspend fun pushVehicleToRemote(v: VehicleEntity) {
        val token = preferences.accessToken ?: return
        val bId = _sessionState.value.businessId ?: return
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return
        try {
            val dto = VehicleDto(
                id = v.remoteId,
                businessId = bId,
                name = v.name,
                numberPlate = v.numberPlate,
                type = v.type,
                notes = v.notes
            )
            val res = supabaseClient.upsertVehicle(dto, token)
            if (res.isSuccess) {
                vehicleDao.updateVehicle(v.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(tag, "pushVehicleToRemote error", e)
        }
    }

    private suspend fun pushDriverToRemote(d: DriverEntity) {
        val token = preferences.accessToken ?: return
        val bId = _sessionState.value.businessId ?: return
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return
        try {
            val assignedRemoteVehicleId = d.assignedVehicleId?.let { vId ->
                vehicleDao.getVehicleByIdDirect(vId)?.remoteId
            }
            val dto = DriverDto(
                id = d.remoteId,
                businessId = bId,
                name = d.name,
                phone = d.phone,
                paymentType = d.paymentType,
                rate = d.rate,
                assignedVehicleId = assignedRemoteVehicleId,
                notes = d.notes
            )
            val res = supabaseClient.upsertDriver(dto, token)
            if (res.isSuccess) {
                driverDao.updateDriver(d.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(tag, "pushDriverToRemote error", e)
        }
    }

    private suspend fun pushBookingToRemote(b: BookingEntity) {
        val token = preferences.accessToken ?: return
        val bId = _sessionState.value.businessId ?: return
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return
        try {
            var vehicle = vehicleDao.getVehicleByIdDirect(b.vehicleId) ?: return
            if (vehicle.remoteId.isBlank()) {
                val newRId = UUID.randomUUID().toString()
                vehicle = vehicle.copy(remoteId = newRId, businessId = bId)
                vehicleDao.updateVehicle(vehicle)
                pushVehicleToRemote(vehicle)
            }
            var driverRemoteId: String? = null
            if (b.driverId != null) {
                var d = driverDao.getAllDriversDirect().find { it.id == b.driverId }
                if (d != null) {
                    if (d.remoteId.isBlank()) {
                        val newRId = UUID.randomUUID().toString()
                        d = d.copy(remoteId = newRId, businessId = bId)
                        driverDao.updateDriver(d)
                        pushDriverToRemote(d)
                    }
                    driverRemoteId = d.remoteId
                }
            }
            val dto = BookingDto(
                id = b.remoteId,
                businessId = bId,
                vehicleId = vehicle.remoteId,
                driverId = driverRemoteId,
                date = b.date,
                customerRoute = b.customerRoute,
                amount = b.amount,
                paymentStatus = b.paymentStatus,
                notes = b.notes
            )
            val res = supabaseClient.upsertBooking(dto, token)
            if (res.isSuccess) {
                bookingDao.updateBooking(b.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(tag, "pushBookingToRemote error", e)
        }
    }

    private suspend fun pushExpenseToRemote(e: ExpenseEntity) {
        val token = preferences.accessToken ?: return
        val bId = _sessionState.value.businessId ?: return
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return
        try {
            var vehicle = vehicleDao.getVehicleByIdDirect(e.vehicleId) ?: return
            if (vehicle.remoteId.isBlank()) {
                val newRId = UUID.randomUUID().toString()
                vehicle = vehicle.copy(remoteId = newRId, businessId = bId)
                vehicleDao.updateVehicle(vehicle)
                pushVehicleToRemote(vehicle)
            }
            var driverRemoteId: String? = null
            if (e.driverId != null) {
                var d = driverDao.getAllDriversDirect().find { it.id == e.driverId }
                if (d != null) {
                    if (d.remoteId.isBlank()) {
                        val newRId = UUID.randomUUID().toString()
                        d = d.copy(remoteId = newRId, businessId = bId)
                        driverDao.updateDriver(d)
                        pushDriverToRemote(d)
                    }
                    driverRemoteId = d.remoteId
                }
            }
            val dto = ExpenseDto(
                id = e.remoteId,
                businessId = bId,
                vehicleId = vehicle.remoteId,
                driverId = driverRemoteId,
                category = e.category,
                amount = e.amount,
                date = e.date,
                notes = e.notes
            )
            val res = supabaseClient.upsertExpense(dto, token)
            if (res.isSuccess) {
                expenseDao.updateExpense(e.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(tag, "pushExpenseToRemote error", e)
        }
    }

    private suspend fun pushDriverPaymentToRemote(p: DriverPaymentEntity) {
        val token = preferences.accessToken ?: return
        val bId = _sessionState.value.businessId ?: return
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return
        try {
            var driver = driverDao.getAllDriversDirect().find { it.id == p.driverId } ?: return
            if (driver.remoteId.isBlank()) {
                val newRId = UUID.randomUUID().toString()
                driver = driver.copy(remoteId = newRId, businessId = bId)
                driverDao.updateDriver(driver)
                pushDriverToRemote(driver)
            }
            var vehicleRemoteId: String? = null
            if (p.vehicleId != null) {
                var v = vehicleDao.getVehicleByIdDirect(p.vehicleId)
                if (v != null) {
                    if (v.remoteId.isBlank()) {
                        val newRId = UUID.randomUUID().toString()
                        v = v.copy(remoteId = newRId, businessId = bId)
                        vehicleDao.updateVehicle(v)
                        pushVehicleToRemote(v)
                    }
                    vehicleRemoteId = v.remoteId
                }
            }
            val dto = DriverPaymentDto(
                id = p.remoteId,
                businessId = bId,
                driverId = driver.remoteId,
                vehicleId = vehicleRemoteId,
                amount = p.amount,
                date = p.date,
                notes = p.notes
            )
            val res = supabaseClient.upsertDriverPayment(dto, token)
            if (res.isSuccess) {
                driverPaymentDao.updatePayment(p.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(tag, "pushDriverPaymentToRemote error", e)
        }
    }

    // ========================================================================
    // AUTHENTICATION & SESSION MANAGEMENT
    // ========================================================================

    suspend fun signUp(email: String, password: String, name: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        val res = supabaseClient.signUp(email, password, name)
        if (res.isSuccess) {
            var session = res.getOrThrow()
            if (session.accessToken.isBlank()) {
                val signInRes = supabaseClient.signIn(email, password)
                if (signInRes.isSuccess) {
                    session = signInRes.getOrThrow()
                }
            }
            preferences.accessToken = session.accessToken
            preferences.refreshToken = session.refreshToken
            preferences.userId = session.user?.id
            preferences.userEmail = session.user?.email ?: email
            preferences.userName = name

            // Check if profile exists, or create initial profile
            val profRes = session.user?.id?.let { uid ->
                val existing = if (session.accessToken.isNotBlank()) {
                    supabaseClient.getProfile(uid, session.accessToken).getOrNull()
                } else null

                if (existing == null && session.accessToken.isNotBlank()) {
                    val newProf = ProfileDto(
                        id = uid,
                        businessId = null,
                        name = name,
                        email = email,
                        role = UserRole.DRIVER
                    )
                    supabaseClient.createProfile(newProf, session.accessToken).getOrNull()
                } else existing
            }

            _sessionState.value = UserSessionState(
                isAuthenticated = true,
                userId = session.user?.id.orEmpty(),
                email = session.user?.email ?: email,
                name = profRes?.name ?: name,
                role = profRes?.role ?: UserRole.DRIVER,
                businessId = profRes?.businessId,
                businessName = null,
                businessJoinCode = null
            )
            _syncStatus.value = SyncStatus.SYNCED
            return@withContext Result.success(session)
        }
        res
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        val res = supabaseClient.signIn(email, password)
        if (res.isSuccess) {
            val session = res.getOrThrow()
            preferences.accessToken = session.accessToken
            preferences.refreshToken = session.refreshToken
            preferences.userId = session.user?.id
            preferences.userEmail = session.user?.email ?: email

            refreshProfileAndBusiness()
            startRealtimeListener()
            triggerSync()
        }
        res
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = preferences.accessToken
        if (token != null) {
            supabaseClient.signOut(token)
        }
        realtimeManager?.stop()
        preferences.clearAuth()
        _sessionState.value = UserSessionState(isAuthenticated = false)
        _syncStatus.value = if (supabaseClient.isConfigured()) SyncStatus.SYNCED else SyncStatus.UNCONFIGURED
    }

    private suspend fun refreshProfileAndBusiness() = withContext(Dispatchers.IO) {
        var token = preferences.accessToken ?: return@withContext
        val uid = preferences.userId ?: return@withContext

        var profRes = supabaseClient.getProfile(uid, token).getOrNull()
        if (profRes == null && preferences.refreshToken != null) {
            val refreshRes = supabaseClient.refreshSession(preferences.refreshToken!!)
            if (refreshRes.isSuccess) {
                val newSession = refreshRes.getOrThrow()
                preferences.accessToken = newSession.accessToken
                preferences.refreshToken = newSession.refreshToken
                token = newSession.accessToken
                profRes = supabaseClient.getProfile(uid, token).getOrNull()
            }
        }

        if (profRes == null) {
            val newProf = ProfileDto(
                id = uid,
                businessId = preferences.businessId,
                name = preferences.userName ?: "User",
                email = preferences.userEmail ?: "",
                role = UserRole.fromString(preferences.userRole)
            )
            profRes = supabaseClient.createProfile(newProf, token).getOrNull() ?: newProf
        }

        preferences.userName = profRes.name
        preferences.userRole = profRes.role.name
        preferences.businessId = profRes.businessId

        var bDto: BusinessDto? = null
        if (profRes.businessId != null) {
            bDto = supabaseClient.getBusinessById(profRes.businessId, token).getOrNull()
            if (bDto != null) {
                preferences.businessName = bDto.name
                preferences.businessJoinCode = bDto.joinCode
            }
        }

        _sessionState.value = UserSessionState(
            isAuthenticated = true,
            userId = uid,
            email = profRes.email.ifBlank { preferences.userEmail.orEmpty() },
            name = profRes.name,
            role = profRes.role,
            businessId = profRes.businessId,
            businessName = bDto?.name ?: preferences.businessName,
            businessJoinCode = bDto?.joinCode ?: preferences.businessJoinCode
        )
    }

    // ========================================================================
    // BUSINESS & TEAM MANAGEMENT
    // ========================================================================

    suspend fun createBusiness(businessName: String): Result<BusinessDto> = withContext(Dispatchers.IO) {
        val token = preferences.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))
        val uid = preferences.userId ?: return@withContext Result.failure(Exception("No user id"))

        if (_sessionState.value.businessId != null) {
            return@withContext Result.failure(Exception("Users cannot change their business once assigned."))
        }

        // Generate a human-friendly unique join code e.g. "RB-7842"
        val randomSuffix = (1000..9999).random()
        val joinCode = "RB-$randomSuffix"

        val createRes = supabaseClient.createBusiness(businessName, joinCode, token)
        if (createRes.isSuccess) {
            val bDto = createRes.getOrThrow()

            preferences.businessId = bDto.id
            preferences.businessName = bDto.name
            preferences.businessJoinCode = bDto.joinCode
            preferences.userRole = UserRole.ADMIN.name

            _sessionState.value = _sessionState.value.copy(
                businessId = bDto.id,
                businessName = bDto.name,
                businessJoinCode = bDto.joinCode,
                role = UserRole.ADMIN
            )

            // Perform one-time migration of existing local data
            migrateLocalDataIfNeeded(bDto.id)
            startRealtimeListener()
            triggerSync()
        }
        createRes
    }

    suspend fun joinBusinessByCode(joinCode: String, requestedRole: UserRole = UserRole.DRIVER): Result<BusinessDto> = withContext(Dispatchers.IO) {
        val token = preferences.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))
        val uid = preferences.userId ?: return@withContext Result.failure(Exception("No user id"))

        if (_sessionState.value.businessId != null) {
            return@withContext Result.failure(Exception("Users cannot change their business once assigned."))
        }

        val joinRes = supabaseClient.joinBusinessByCode(joinCode, token)
        if (joinRes.isSuccess) {
            val bDto = joinRes.getOrThrow()
            // Joining user is always created as DRIVER
            val assignedRole = UserRole.DRIVER

            preferences.businessId = bDto.id
            preferences.businessName = bDto.name
            preferences.businessJoinCode = bDto.joinCode
            preferences.userRole = assignedRole.name

            _sessionState.value = _sessionState.value.copy(
                businessId = bDto.id,
                businessName = bDto.name,
                businessJoinCode = bDto.joinCode,
                role = assignedRole
            )

            // Migrate any local data if this device had standalone data
            migrateLocalDataIfNeeded(bDto.id)
            startRealtimeListener()
            triggerSync()
            Result.success(bDto)
        } else {
            joinRes
        }
    }

    suspend fun fetchTeamMembers(): Result<List<ProfileDto>> = withContext(Dispatchers.IO) {
        val token = preferences.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))
        val bId = _sessionState.value.businessId ?: return@withContext Result.failure(Exception("No business joined"))
        supabaseClient.getBusinessMembers(bId, token)
    }

    suspend fun updateMemberRole(userId: String, newRole: UserRole): Result<ProfileDto> = withContext(Dispatchers.IO) {
        val token = preferences.accessToken ?: return@withContext Result.failure(Exception("Not logged in"))
        if (_sessionState.value.role != UserRole.ADMIN) {
            return@withContext Result.failure(Exception("Only ADMINs can change member roles."))
        }
        if (userId == _sessionState.value.userId) {
            return@withContext Result.failure(Exception("Users cannot change their own role."))
        }
        val existingProf = supabaseClient.getProfile(userId, token).getOrNull()
            ?: return@withContext Result.failure(Exception("Profile not found"))

        val updated = existingProf.copy(role = newRole)
        supabaseClient.updateProfile(updated, token)
    }

    // ========================================================================
    // ONE-TIME LOCAL DATA MIGRATION TO SUPABASE
    // ========================================================================

    suspend fun migrateLocalDataIfNeeded(businessId: String) = withContext(Dispatchers.IO) {
        if (preferences.isLocalDataMigrated) return@withContext
        val token = preferences.accessToken ?: return@withContext
        if (!networkMonitor.isOnline.value) return@withContext

        try {
            _syncStatus.value = SyncStatus.SYNCING
            Log.d(tag, "Starting one-time migration of local data to Supabase business $businessId")

            // 1. Vehicles
            val localVehicles = vehicleDao.getAllVehiclesDirect()
            for (v in localVehicles) {
                val remoteId = if (v.remoteId.isNotBlank()) v.remoteId else UUID.randomUUID().toString()
                val updatedV = v.copy(remoteId = remoteId, businessId = businessId)
                val dto = VehicleDto(
                    id = remoteId,
                    businessId = businessId,
                    name = v.name,
                    numberPlate = v.numberPlate,
                    type = v.type,
                    notes = v.notes
                )
                supabaseClient.upsertVehicle(dto, token)
                vehicleDao.updateVehicle(updatedV.copy(isSynced = true))
            }

            // 2. Drivers
            val localDrivers = driverDao.getAllDriversDirect()
            for (d in localDrivers) {
                val remoteId = if (d.remoteId.isNotBlank()) d.remoteId else UUID.randomUUID().toString()
                val assignedRemoteVehicleId = d.assignedVehicleId?.let { vId ->
                    vehicleDao.getVehicleByIdDirect(vId)?.remoteId
                }
                val updatedD = d.copy(remoteId = remoteId, businessId = businessId)
                val dto = DriverDto(
                    id = remoteId,
                    businessId = businessId,
                    name = d.name,
                    phone = d.phone,
                    paymentType = d.paymentType,
                    rate = d.rate,
                    assignedVehicleId = assignedRemoteVehicleId,
                    notes = d.notes
                )
                supabaseClient.upsertDriver(dto, token)
                driverDao.updateDriver(updatedD.copy(isSynced = true))
            }

            // 3. Bookings
            val localBookings = bookingDao.getAllBookingsDirect()
            for (b in localBookings) {
                val remoteId = if (b.remoteId.isNotBlank()) b.remoteId else UUID.randomUUID().toString()
                val v = vehicleDao.getVehicleByIdDirect(b.vehicleId) ?: continue
                val driverRemoteId = b.driverId?.let { dId ->
                    driverDao.getAllDriversDirect().find { it.id == dId }?.remoteId
                }
                val updatedB = b.copy(remoteId = remoteId, businessId = businessId)
                val dto = BookingDto(
                    id = remoteId,
                    businessId = businessId,
                    vehicleId = v.remoteId,
                    driverId = driverRemoteId,
                    date = b.date,
                    customerRoute = b.customerRoute,
                    amount = b.amount,
                    paymentStatus = b.paymentStatus,
                    notes = b.notes
                )
                supabaseClient.upsertBooking(dto, token)
                bookingDao.updateBooking(updatedB.copy(isSynced = true))
            }

            // 4. Expenses
            val localExpenses = expenseDao.getAllExpensesDirect()
            for (e in localExpenses) {
                val remoteId = if (e.remoteId.isNotBlank()) e.remoteId else UUID.randomUUID().toString()
                val v = vehicleDao.getVehicleByIdDirect(e.vehicleId) ?: continue
                val driverRemoteId = e.driverId?.let { dId ->
                    driverDao.getAllDriversDirect().find { it.id == dId }?.remoteId
                }
                val updatedE = e.copy(remoteId = remoteId, businessId = businessId)
                val dto = ExpenseDto(
                    id = remoteId,
                    businessId = businessId,
                    vehicleId = v.remoteId,
                    driverId = driverRemoteId,
                    category = e.category,
                    amount = e.amount,
                    date = e.date,
                    notes = e.notes
                )
                supabaseClient.upsertExpense(dto, token)
                expenseDao.updateExpense(updatedE.copy(isSynced = true))
            }

            // 5. Driver Payments
            val localPayments = driverPaymentDao.getAllDriverPaymentsDirect()
            for (p in localPayments) {
                val remoteId = if (p.remoteId.isNotBlank()) p.remoteId else UUID.randomUUID().toString()
                val d = driverDao.getAllDriversDirect().find { it.id == p.driverId } ?: continue
                val vehicleRemoteId = p.vehicleId?.let { vId ->
                    vehicleDao.getVehicleByIdDirect(vId)?.remoteId
                }
                val updatedP = p.copy(remoteId = remoteId, businessId = businessId)
                val dto = DriverPaymentDto(
                    id = remoteId,
                    businessId = businessId,
                    driverId = d.remoteId,
                    vehicleId = vehicleRemoteId,
                    amount = p.amount,
                    date = p.date,
                    notes = p.notes
                )
                supabaseClient.upsertDriverPayment(dto, token)
                driverPaymentDao.updatePayment(updatedP.copy(isSynced = true))
            }

            preferences.isLocalDataMigrated = true
            _syncStatus.value = SyncStatus.SYNCED
            Log.d(tag, "One-time migration complete!")
        } catch (e: Exception) {
            Log.e(tag, "Migration error", e)
            _syncStatus.value = SyncStatus.SYNCED
        }
    }

    // ========================================================================
    // BIDIRECTIONAL SYNC (Pull Remote & Push Pending)
    // ========================================================================

    suspend fun triggerSync() = withContext(Dispatchers.IO) {
        val token = preferences.accessToken ?: return@withContext
        val bId = _sessionState.value.businessId ?: return@withContext
        if (!networkMonitor.isOnline.value || !supabaseClient.isConfigured()) return@withContext

        try {
            _syncStatus.value = SyncStatus.SYNCING

            // 1. Sync pending unsynced records to Supabase
            syncPendingLocalRecords()

            // 2. Fetch latest from Supabase and reconcile with Room
            val remoteVehicles = supabaseClient.getVehicles(bId, token).getOrDefault(emptyList())
            for (dto in remoteVehicles) {
                val existing = vehicleDao.getVehicleByRemoteId(dto.id)
                if (existing != null) {
                    vehicleDao.updateVehicle(
                        existing.copy(
                            name = dto.name,
                            numberPlate = dto.numberPlate,
                            type = dto.type,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                } else {
                    vehicleDao.insertVehicle(
                        VehicleEntity(
                            remoteId = dto.id,
                            businessId = bId,
                            name = dto.name,
                            numberPlate = dto.numberPlate,
                            type = dto.type,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                }
            }

            val remoteDrivers = supabaseClient.getDrivers(bId, token).getOrDefault(emptyList())
            for (dto in remoteDrivers) {
                val assignedLocalVehicleId = dto.assignedVehicleId?.let { rId ->
                    vehicleDao.getVehicleByRemoteId(rId)?.id
                }
                val existing = driverDao.getDriverByRemoteId(dto.id)
                if (existing != null) {
                    driverDao.updateDriver(
                        existing.copy(
                            name = dto.name,
                            phone = dto.phone,
                            paymentType = dto.paymentType,
                            rate = dto.rate,
                            assignedVehicleId = assignedLocalVehicleId,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                } else {
                    driverDao.insertDriver(
                        DriverEntity(
                            remoteId = dto.id,
                            businessId = bId,
                            name = dto.name,
                            phone = dto.phone,
                            paymentType = dto.paymentType,
                            rate = dto.rate,
                            assignedVehicleId = assignedLocalVehicleId,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                }
            }

            val remoteBookings = supabaseClient.getBookings(bId, token).getOrDefault(emptyList())
            for (dto in remoteBookings) {
                val localVehicle = vehicleDao.getVehicleByRemoteId(dto.vehicleId) ?: continue
                val localDriverId = dto.driverId?.let { dRemoteId ->
                    driverDao.getDriverByRemoteId(dRemoteId)?.id
                }
                val existing = bookingDao.getBookingByRemoteId(dto.id)
                if (existing != null) {
                    bookingDao.updateBooking(
                        existing.copy(
                            vehicleId = localVehicle.id,
                            driverId = localDriverId,
                            date = dto.date,
                            customerRoute = dto.customerRoute,
                            amount = dto.amount,
                            paymentStatus = dto.paymentStatus,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                } else {
                    bookingDao.insertBooking(
                        BookingEntity(
                            remoteId = dto.id,
                            businessId = bId,
                            vehicleId = localVehicle.id,
                            driverId = localDriverId,
                            date = dto.date,
                            customerRoute = dto.customerRoute,
                            amount = dto.amount,
                            paymentStatus = dto.paymentStatus,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                }
            }

            val remoteExpenses = supabaseClient.getExpenses(bId, token).getOrDefault(emptyList())
            for (dto in remoteExpenses) {
                val localVehicle = vehicleDao.getVehicleByRemoteId(dto.vehicleId) ?: continue
                val localDriverId = dto.driverId?.let { dRemoteId ->
                    driverDao.getDriverByRemoteId(dRemoteId)?.id
                }
                val existing = expenseDao.getExpenseByRemoteId(dto.id)
                if (existing != null) {
                    expenseDao.updateExpense(
                        existing.copy(
                            vehicleId = localVehicle.id,
                            driverId = localDriverId,
                            category = dto.category,
                            amount = dto.amount,
                            date = dto.date,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                } else {
                    expenseDao.insertExpense(
                        ExpenseEntity(
                            remoteId = dto.id,
                            businessId = bId,
                            vehicleId = localVehicle.id,
                            driverId = localDriverId,
                            category = dto.category,
                            amount = dto.amount,
                            date = dto.date,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                }
            }

            val remotePayments = supabaseClient.getDriverPayments(bId, token).getOrDefault(emptyList())
            for (dto in remotePayments) {
                val localDriver = driverDao.getDriverByRemoteId(dto.driverId) ?: continue
                val localVehicleId = dto.vehicleId?.let { vRemoteId ->
                    vehicleDao.getVehicleByRemoteId(vRemoteId)?.id
                }
                val existing = driverPaymentDao.getDriverPaymentByRemoteId(dto.id)
                if (existing != null) {
                    driverPaymentDao.updatePayment(
                        existing.copy(
                            driverId = localDriver.id,
                            vehicleId = localVehicleId,
                            amount = dto.amount,
                            date = dto.date,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                } else {
                    driverPaymentDao.insertPayment(
                        DriverPaymentEntity(
                            remoteId = dto.id,
                            businessId = bId,
                            driverId = localDriver.id,
                            vehicleId = localVehicleId,
                            amount = dto.amount,
                            date = dto.date,
                            notes = dto.notes,
                            isSynced = true
                        )
                    )
                }
            }

            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.e(tag, "triggerSync error", e)
            _syncStatus.value = SyncStatus.SYNCED
        }
    }

    private suspend fun syncPendingLocalRecords() {
        // Push any vehicle, driver, booking, expense, or payment marked isSynced = false
        val unsyncedV = vehicleDao.getUnsyncedVehicles()
        for (v in unsyncedV) pushVehicleToRemote(v)

        val unsyncedD = driverDao.getUnsyncedDrivers()
        for (d in unsyncedD) pushDriverToRemote(d)

        val unsyncedB = bookingDao.getUnsyncedBookings()
        for (b in unsyncedB) pushBookingToRemote(b)

        val unsyncedE = expenseDao.getUnsyncedExpenses()
        for (e in unsyncedE) pushExpenseToRemote(e)

        val unsyncedP = driverPaymentDao.getUnsyncedDriverPayments()
        for (p in unsyncedP) pushDriverPaymentToRemote(p)
    }

    // ========================================================================
    // SUPABASE REALTIME WEBSOCKET LISTENER
    // ========================================================================

    private fun startRealtimeListener() {
        if (!supabaseClient.isConfigured() || _sessionState.value.businessId == null) return
        realtimeManager?.stop()

        realtimeManager = SupabaseRealtimeManager(
            client = supabaseClient,
            coroutineScope = repoScope,
            authTokenProvider = { preferences.accessToken },
            onTableChanged = { table, action, record, oldRecord ->
                repoScope.launch {
                    handleRealtimeChange(table, action, record, oldRecord)
                }
            }
        ).also { it.start() }
    }

    private suspend fun handleRealtimeChange(table: String, action: String, record: JSONObject?, oldRecord: JSONObject?) {
        val currentBId = _sessionState.value.businessId ?: return

        when (table) {
            "vehicles" -> {
                if (action == "DELETE") {
                    val rId = oldRecord?.optString("id") ?: record?.optString("id") ?: return
                    vehicleDao.deleteVehicleByRemoteId(rId)
                } else if (record != null) {
                    val bId = record.optString("business_id")
                    if (bId != currentBId) return
                    val dto = VehicleDto.fromJson(record)
                    val existing = vehicleDao.getVehicleByRemoteId(dto.id)
                    if (existing != null) {
                        vehicleDao.updateVehicle(existing.copy(name = dto.name, numberPlate = dto.numberPlate, type = dto.type, notes = dto.notes, isSynced = true))
                    } else {
                        vehicleDao.insertVehicle(VehicleEntity(remoteId = dto.id, businessId = bId, name = dto.name, numberPlate = dto.numberPlate, type = dto.type, notes = dto.notes, isSynced = true))
                    }
                }
            }
            "drivers" -> {
                if (action == "DELETE") {
                    val rId = oldRecord?.optString("id") ?: record?.optString("id") ?: return
                    driverDao.deleteDriverByRemoteId(rId)
                } else if (record != null) {
                    val bId = record.optString("business_id")
                    if (bId != currentBId) return
                    val dto = DriverDto.fromJson(record)
                    val localVId = dto.assignedVehicleId?.let { rId -> vehicleDao.getVehicleByRemoteId(rId)?.id }
                    val existing = driverDao.getDriverByRemoteId(dto.id)
                    if (existing != null) {
                        driverDao.updateDriver(existing.copy(name = dto.name, phone = dto.phone, paymentType = dto.paymentType, rate = dto.rate, assignedVehicleId = localVId, notes = dto.notes, isSynced = true))
                    } else {
                        driverDao.insertDriver(DriverEntity(remoteId = dto.id, businessId = bId, name = dto.name, phone = dto.phone, paymentType = dto.paymentType, rate = dto.rate, assignedVehicleId = localVId, notes = dto.notes, isSynced = true))
                    }
                }
            }
            "bookings" -> {
                if (action == "DELETE") {
                    val rId = oldRecord?.optString("id") ?: record?.optString("id") ?: return
                    bookingDao.deleteBookingByRemoteId(rId)
                } else if (record != null) {
                    val bId = record.optString("business_id")
                    if (bId != currentBId) return
                    val dto = BookingDto.fromJson(record)
                    val localVehicle = vehicleDao.getVehicleByRemoteId(dto.vehicleId)
                    if (localVehicle == null) {
                        // Vehicle might not yet be in local DB; pull and sync
                        triggerSync()
                        return
                    }
                    val localDriverId = dto.driverId?.let { rId -> driverDao.getDriverByRemoteId(rId)?.id }
                    val existing = bookingDao.getBookingByRemoteId(dto.id)
                    if (existing != null) {
                        bookingDao.updateBooking(existing.copy(vehicleId = localVehicle.id, driverId = localDriverId, date = dto.date, customerRoute = dto.customerRoute, amount = dto.amount, paymentStatus = dto.paymentStatus, notes = dto.notes, isSynced = true))
                    } else {
                        bookingDao.insertBooking(BookingEntity(remoteId = dto.id, businessId = bId, vehicleId = localVehicle.id, driverId = localDriverId, date = dto.date, customerRoute = dto.customerRoute, amount = dto.amount, paymentStatus = dto.paymentStatus, notes = dto.notes, isSynced = true))
                    }
                }
            }
            "expenses" -> {
                if (action == "DELETE") {
                    val rId = oldRecord?.optString("id") ?: record?.optString("id") ?: return
                    expenseDao.deleteExpenseByRemoteId(rId)
                } else if (record != null) {
                    val bId = record.optString("business_id")
                    if (bId != currentBId) return
                    val dto = ExpenseDto.fromJson(record)
                    val localVehicle = vehicleDao.getVehicleByRemoteId(dto.vehicleId)
                    if (localVehicle == null) {
                        triggerSync()
                        return
                    }
                    val localDriverId = dto.driverId?.let { rId -> driverDao.getDriverByRemoteId(rId)?.id }
                    val existing = expenseDao.getExpenseByRemoteId(dto.id)
                    if (existing != null) {
                        expenseDao.updateExpense(existing.copy(vehicleId = localVehicle.id, driverId = localDriverId, category = dto.category, amount = dto.amount, date = dto.date, notes = dto.notes, isSynced = true))
                    } else {
                        expenseDao.insertExpense(ExpenseEntity(remoteId = dto.id, businessId = bId, vehicleId = localVehicle.id, driverId = localDriverId, category = dto.category, amount = dto.amount, date = dto.date, notes = dto.notes, isSynced = true))
                    }
                }
            }
            "driver_payments" -> {
                if (action == "DELETE") {
                    val rId = oldRecord?.optString("id") ?: record?.optString("id") ?: return
                    driverPaymentDao.deleteDriverPaymentByRemoteId(rId)
                } else if (record != null) {
                    val bId = record.optString("business_id")
                    if (bId != currentBId) return
                    val dto = DriverPaymentDto.fromJson(record)
                    val localDriver = driverDao.getDriverByRemoteId(dto.driverId)
                    if (localDriver == null) {
                        triggerSync()
                        return
                    }
                    val localVId = dto.vehicleId?.let { rId -> vehicleDao.getVehicleByRemoteId(rId)?.id }
                    val existing = driverPaymentDao.getDriverPaymentByRemoteId(dto.id)
                    if (existing != null) {
                        driverPaymentDao.updatePayment(existing.copy(driverId = localDriver.id, vehicleId = localVId, amount = dto.amount, date = dto.date, notes = dto.notes, isSynced = true))
                    } else {
                        driverPaymentDao.insertPayment(DriverPaymentEntity(remoteId = dto.id, businessId = bId, driverId = localDriver.id, vehicleId = localVId, amount = dto.amount, date = dto.date, notes = dto.notes, isSynced = true))
                    }
                }
            }
            "profiles" -> {
                if (record != null) {
                    val uid = record.optString("id")
                    if (uid == _sessionState.value.userId) {
                        val newRole = UserRole.fromString(record.optString("role"))
                        val newBId = record.optString("business_id").takeIf { it.isNotBlank() }
                        _sessionState.value = _sessionState.value.copy(
                            role = newRole,
                            businessId = newBId ?: _sessionState.value.businessId
                        )
                    }
                }
            }
        }
    }

    // ========================================================================
    // SETTINGS CREDENTIAL CONFIGURATION
    // ========================================================================

    fun getSupabaseUrl(): String = preferences.customSupabaseUrl ?: (supabaseClient.customBaseUrl.orEmpty())
    fun getSupabaseAnonKey(): String = preferences.customSupabaseKey ?: (supabaseClient.customApiKey.orEmpty())
    fun isSupabaseConfigured(): Boolean = supabaseClient.isConfigured()

    fun setCustomSupabaseCredentials(url: String, key: String) {
        preferences.customSupabaseUrl = url.trim()
        preferences.customSupabaseKey = key.trim()
        supabaseClient.customBaseUrl = url.trim()
        supabaseClient.customApiKey = key.trim()

        if (supabaseClient.isConfigured()) {
            _syncStatus.value = SyncStatus.SYNCED
            if (_sessionState.value.isAuthenticated) {
                repoScope.launch {
                    triggerSync()
                    startRealtimeListener()
                }
            }
        } else {
            _syncStatus.value = SyncStatus.UNCONFIGURED
        }
    }

    suspend fun seedSampleData() = withContext(Dispatchers.IO) {
        val bId = _sessionState.value.businessId.orEmpty()
        val v1Id = insertVehicle(VehicleEntity(name = "Tata 407 Super", numberPlate = "MH 12 QX 4521", type = "Mini Truck", notes = "Primary cargo shuttle", businessId = bId))
        val v2Id = insertVehicle(VehicleEntity(name = "Ashok Leyland 1616", numberPlate = "MH 14 AB 9087", type = "Truck", notes = "Heavy interstate haulage", businessId = bId))
        val v3Id = insertVehicle(VehicleEntity(name = "Eicher Pro 2049", numberPlate = "MH 04 BK 3312", type = "Truck", notes = "Local FMCG delivery", businessId = bId))

        val d1Id = insertDriver(DriverEntity(name = "Ramesh Kumar", phone = "+91 98230 45612", paymentType = "per_trip", rate = 850.0, assignedVehicleId = v1Id, notes = "Ghat driver", businessId = bId))
        val d2Id = insertDriver(DriverEntity(name = "Suresh Yadav", phone = "+91 97654 32189", paymentType = "fixed_salary", rate = 26000.0, assignedVehicleId = v2Id, notes = "Container specialist", businessId = bId))
        val d3Id = insertDriver(DriverEntity(name = "Vikram Singh", phone = "+91 91234 56780", paymentType = "per_trip", rate = 600.0, assignedVehicleId = v3Id, notes = "City deliveries", businessId = bId))

        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val dayMillis = 24 * 60 * 60 * 1000L

        insertBooking(BookingEntity(vehicleId = v1Id, driverId = d1Id, date = now - 1 * dayMillis, customerRoute = "Bajaj Auto - Chakan to JNPT Port", amount = 14500.0, paymentStatus = "PAID", notes = "Export spare parts", businessId = bId))
        insertBooking(BookingEntity(vehicleId = v2Id, driverId = d2Id, date = now - 2 * dayMillis, customerRoute = "Reliance Retail - Pune to Surat", amount = 32000.0, paymentStatus = "PAID", notes = "Stock replenishment", businessId = bId))
        insertBooking(BookingEntity(vehicleId = v3Id, driverId = d3Id, date = now - 3 * dayMillis, customerRoute = "Amul Dairy - Katraj to Vashi Hub", amount = 8800.0, paymentStatus = "PENDING", notes = "Cold chain products", businessId = bId))

        insertExpense(ExpenseEntity(vehicleId = v1Id, category = "Fuel", amount = 4200.0, date = now - 1 * dayMillis, notes = "Diesel Shell Chakan", businessId = bId))
        insertExpense(ExpenseEntity(vehicleId = v2Id, category = "Fuel", amount = 9800.0, date = now - 2 * dayMillis, notes = "Highway diesel 110L", businessId = bId))
        insertExpense(ExpenseEntity(vehicleId = v1Id, category = "Toll", amount = 640.0, date = now - 1 * dayMillis, notes = "Expressway Fastag", businessId = bId))

        insertDriverPayment(DriverPaymentEntity(driverId = d1Id, vehicleId = v1Id, date = now - 1 * dayMillis, amount = 1700.0, notes = "2 trips payout", businessId = bId))
        insertDriverPayment(DriverPaymentEntity(driverId = d2Id, vehicleId = v2Id, date = now - 10 * dayMillis, amount = 13000.0, notes = "Advance salary", businessId = bId))
    }
}

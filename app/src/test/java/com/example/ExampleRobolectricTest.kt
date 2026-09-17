package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.RoadBookDatabase
import com.example.data.entity.BookingEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.VehicleEntity
import com.example.data.remote.BusinessDto
import com.example.data.remote.ProfileDto
import com.example.data.remote.UserRole
import com.example.data.remote.VehicleDto
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: RoadBookDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RoadBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RoadBook", appName)
    }

    @Test
    fun `insert vehicle with sync fields and retrieve`() = runBlocking {
        val remoteId = UUID.randomUUID().toString()
        val vehicle = VehicleEntity(
            remoteId = remoteId,
            businessId = "biz-123",
            name = "Tata 407",
            numberPlate = "MH 12 AB 1234",
            type = "Mini Truck",
            notes = "Test vehicle",
            isSynced = false
        )
        val id = db.vehicleDao().insertVehicle(vehicle)
        assertTrue(id > 0)

        val retrieved = db.vehicleDao().getVehicleByIdDirect(id)
        assertNotNull(retrieved)
        assertEquals("Tata 407", retrieved?.name)
        assertEquals(remoteId, retrieved?.remoteId)
        assertEquals("biz-123", retrieved?.businessId)
        assertEquals(false, retrieved?.isSynced)

        val unsynced = db.vehicleDao().getUnsyncedVehicles()
        assertEquals(1, unsynced.size)

        db.vehicleDao().updateVehicle(retrieved!!.copy(isSynced = true))
        val unsyncedAfter = db.vehicleDao().getUnsyncedVehicles()
        assertEquals(0, unsyncedAfter.size)
    }

    @Test
    fun `insert driver and booking with sync fields`() = runBlocking {
        val driver = DriverEntity(
            remoteId = "drv-remote-1",
            businessId = "biz-123",
            name = "John Doe",
            phone = "1234567890",
            paymentType = "per_trip",
            rate = 500.0,
            assignedVehicleId = null,
            notes = "Full time",
            isSynced = true
        )
        val dId = db.driverDao().insertDriver(driver)
        assertTrue(dId > 0)

        val booking = BookingEntity(
            remoteId = "book-remote-1",
            businessId = "biz-123",
            vehicleId = 1L,
            driverId = dId,
            date = System.currentTimeMillis(),
            customerRoute = "City A to City B",
            amount = 1500.0,
            paymentStatus = "PAID",
            notes = "Urgent cargo",
            isSynced = true
        )
        val bId = db.bookingDao().insertBooking(booking)
        assertTrue(bId > 0)

        val retrievedBooking = db.bookingDao().getBookingByRemoteId("book-remote-1")
        assertNotNull(retrievedBooking)
        assertEquals(1500.0, retrievedBooking?.amount ?: 0.0, 0.001)
        assertEquals("City A to City B", retrievedBooking?.customerRoute)
    }

    @Test
    fun `supabase DTO serialization and JSON parsing`() {
        val vehicleDto = VehicleDto(
            id = "v-101",
            businessId = "biz-999",
            name = "Eicher Pro",
            numberPlate = "KA 01 MG 5555",
            type = "Truck",
            notes = "Long haul"
        )
        val json = vehicleDto.toJson()
        assertEquals("v-101", json.getString("id"))
        assertEquals("biz-999", json.getString("business_id"))
        assertEquals("Eicher Pro", json.getString("name"))

        val parsed = VehicleDto.fromJson(json)
        assertEquals(vehicleDto.id, parsed.id)
        assertEquals(vehicleDto.name, parsed.name)
        assertEquals(vehicleDto.numberPlate, parsed.numberPlate)

        val profileJson = JSONObject().apply {
            put("id", "user-uuid-1")
            put("business_id", "biz-999")
            put("name", "Alice Manager")
            put("email", "alice@example.com")
            put("role", "MANAGER")
        }
        val profile = ProfileDto.fromJson(profileJson)
        assertEquals(UserRole.MANAGER, profile.role)
        assertEquals("Alice Manager", profile.name)

        val bizJson = JSONObject().apply {
            put("id", "biz-999")
            put("name", "Apex Logistics")
            put("join_code", "RB-5678")
            put("created_at", "2026-09-16T00:00:00Z")
        }
        val biz = BusinessDto.fromJson(bizJson)
        assertEquals("Apex Logistics", biz.name)
        assertEquals("RB-5678", biz.joinCode)
    }
}

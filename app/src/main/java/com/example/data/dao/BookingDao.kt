package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.BookingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY date DESC, createdAt DESC")
    fun getAllBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE vehicleId = :vehicleId ORDER BY date DESC, createdAt DESC")
    fun getBookingsForVehicle(vehicleId: Long): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE driverId = :driverId ORDER BY date DESC, createdAt DESC")
    fun getBookingsForDriver(driverId: Long): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE date >= :sinceDate ORDER BY date DESC, createdAt DESC")
    fun getBookingsSince(sinceDate: Long): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity): Long

    @Update
    suspend fun updateBooking(booking: BookingEntity)

    @Delete
    suspend fun deleteBooking(booking: BookingEntity)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deleteBookingById(id: Long)

    @Query("DELETE FROM bookings WHERE vehicleId = :vehicleId")
    suspend fun deleteBookingsForVehicle(vehicleId: Long)

    @Query("SELECT * FROM bookings WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getBookingByRemoteId(remoteId: String): BookingEntity?

    @Query("SELECT * FROM bookings")
    suspend fun getAllBookingsDirect(): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE isSynced = 0")
    suspend fun getUnsyncedBookings(): List<BookingEntity>

    @Query("DELETE FROM bookings WHERE remoteId = :remoteId")
    suspend fun deleteBookingByRemoteId(remoteId: String)

    @Query("DELETE FROM bookings")
    suspend fun deleteAllBookings()
}

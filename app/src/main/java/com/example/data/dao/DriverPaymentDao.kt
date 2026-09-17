package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.DriverPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverPaymentDao {
    @Query("SELECT * FROM driver_payments ORDER BY date DESC, createdAt DESC")
    fun getAllDriverPayments(): Flow<List<DriverPaymentEntity>>

    @Query("SELECT * FROM driver_payments WHERE driverId = :driverId ORDER BY date DESC, createdAt DESC")
    fun getPaymentsForDriver(driverId: Long): Flow<List<DriverPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: DriverPaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: DriverPaymentEntity)

    @Delete
    suspend fun deletePayment(payment: DriverPaymentEntity)

    @Query("DELETE FROM driver_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Long)

    @Query("DELETE FROM driver_payments WHERE driverId = :driverId")
    suspend fun deletePaymentsForDriver(driverId: Long)

    @Query("SELECT * FROM driver_payments WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getDriverPaymentByRemoteId(remoteId: String): DriverPaymentEntity?

    @Query("SELECT * FROM driver_payments")
    suspend fun getAllDriverPaymentsDirect(): List<DriverPaymentEntity>

    @Query("SELECT * FROM driver_payments WHERE isSynced = 0")
    suspend fun getUnsyncedDriverPayments(): List<DriverPaymentEntity>

    @Query("DELETE FROM driver_payments WHERE remoteId = :remoteId")
    suspend fun deleteDriverPaymentByRemoteId(remoteId: String)

    @Query("DELETE FROM driver_payments")
    suspend fun deleteAllDriverPayments()
}

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.DriverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY name ASC")
    fun getAllDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers WHERE id = :id")
    fun getDriverById(id: Long): Flow<DriverEntity?>

    @Query("SELECT * FROM drivers WHERE assignedVehicleId = :vehicleId LIMIT 1")
    fun getDriverByVehicleId(vehicleId: Long): Flow<DriverEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriver(driver: DriverEntity): Long

    @Update
    suspend fun updateDriver(driver: DriverEntity)

    @Delete
    suspend fun deleteDriver(driver: DriverEntity)

    @Query("DELETE FROM drivers WHERE id = :id")
    suspend fun deleteDriverById(id: Long)

    @Query("SELECT * FROM drivers WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getDriverByRemoteId(remoteId: String): DriverEntity?

    @Query("SELECT * FROM drivers")
    suspend fun getAllDriversDirect(): List<DriverEntity>

    @Query("SELECT * FROM drivers WHERE isSynced = 0")
    suspend fun getUnsyncedDrivers(): List<DriverEntity>

    @Query("DELETE FROM drivers WHERE remoteId = :remoteId")
    suspend fun deleteDriverByRemoteId(remoteId: String)

    @Query("DELETE FROM drivers")
    suspend fun deleteAllDrivers()
}

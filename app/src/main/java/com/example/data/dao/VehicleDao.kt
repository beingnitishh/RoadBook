package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun getVehicleById(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleByIdDirect(id: Long): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicleById(id: Long)

    @Query("SELECT * FROM vehicles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getVehicleByRemoteId(remoteId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehiclesDirect(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE isSynced = 0")
    suspend fun getUnsyncedVehicles(): List<VehicleEntity>

    @Query("DELETE FROM vehicles WHERE remoteId = :remoteId")
    suspend fun deleteVehicleByRemoteId(remoteId: String)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()
}

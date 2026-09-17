package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driver_payments",
    indices = [
        Index(value = ["driverId"]),
        Index(value = ["vehicleId"]),
        Index(value = ["date"]),
        Index(value = ["remoteId"]),
        Index(value = ["businessId"])
    ]
)
data class DriverPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val businessId: String = "",
    val isSynced: Boolean = true,
    val driverId: Long,
    val vehicleId: Long? = null,
    val date: Long,
    val amount: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

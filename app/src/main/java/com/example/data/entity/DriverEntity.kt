package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drivers",
    indices = [
        Index(value = ["assignedVehicleId"]),
        Index(value = ["remoteId"]),
        Index(value = ["businessId"])
    ]
)
data class DriverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val businessId: String = "",
    val isSynced: Boolean = true,
    val name: String,
    val phone: String,
    val paymentType: String, // "per_trip" or "fixed_salary"
    val rate: Double, // Rate per trip or monthly salary amount
    val assignedVehicleId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

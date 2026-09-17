package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["driverId"]),
        Index(value = ["date"]),
        Index(value = ["category"]),
        Index(value = ["remoteId"]),
        Index(value = ["businessId"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val businessId: String = "",
    val isSynced: Boolean = true,
    val vehicleId: Long,
    val driverId: Long? = null,
    val category: String, // "Fuel", "Maintenance", "Toll", "Repair", "Challan/Fine", "Other"
    val amount: Double,
    val date: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

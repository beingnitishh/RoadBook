package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookings",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["driverId"]),
        Index(value = ["date"]),
        Index(value = ["paymentStatus"]),
        Index(value = ["remoteId"]),
        Index(value = ["businessId"])
    ]
)
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val businessId: String = "",
    val isSynced: Boolean = true,
    val vehicleId: Long,
    val driverId: Long? = null,
    val date: Long,
    val customerRoute: String,
    val amount: Double,
    val paymentStatus: String, // "PAID", "PENDING"
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

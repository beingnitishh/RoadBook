package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["remoteId"]),
        Index(value = ["businessId"])
    ]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val businessId: String = "",
    val isSynced: Boolean = true,
    val name: String,
    val numberPlate: String,
    val type: String, // "Truck", "Mini Truck", "Bus", "Van", "Car", "Trailer", "Other"
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

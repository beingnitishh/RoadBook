package com.example.data.remote

import org.json.JSONObject

enum class UserRole {
    ADMIN,
    MANAGER,
    DRIVER;

    companion object {
        fun fromString(value: String?): UserRole {
            return when (value?.uppercase()?.trim()) {
                "ADMIN" -> ADMIN
                "MANAGER" -> MANAGER
                "DRIVER" -> DRIVER
                else -> DRIVER
            }
        }
    }
}

data class AuthUser(
    val id: String,
    val email: String,
    val createdAt: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): AuthUser {
            return AuthUser(
                id = json.optString("id", ""),
                email = json.optString("email", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val user: AuthUser?
) {
    companion object {
        fun fromJson(json: JSONObject): AuthSession {
            val userObj = json.optJSONObject("user")
            return AuthSession(
                accessToken = json.optString("access_token", ""),
                refreshToken = json.optString("refresh_token", null),
                user = userObj?.let { AuthUser.fromJson(it) }
            )
        }
    }
}

data class BusinessDto(
    val id: String,
    val name: String,
    val joinCode: String,
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("join_code", joinCode)
    }

    companion object {
        fun fromJson(json: JSONObject): BusinessDto {
            return BusinessDto(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                joinCode = json.optString("join_code", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class ProfileDto(
    val id: String,
    val businessId: String?,
    val name: String,
    val email: String,
    val role: UserRole,
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        if (businessId != null) put("business_id", businessId)
        put("name", name)
        put("email", email)
        put("role", role.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ProfileDto {
            return ProfileDto(
                id = json.optString("id", ""),
                businessId = if (json.has("business_id") && !json.isNull("business_id")) json.optString("business_id") else null,
                name = json.optString("name", ""),
                email = json.optString("email", ""),
                role = UserRole.fromString(json.optString("role", "DRIVER")),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class VehicleDto(
    val id: String,
    val businessId: String,
    val name: String,
    val numberPlate: String,
    val type: String,
    val notes: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("name", name)
        put("number_plate", numberPlate)
        put("type", type)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): VehicleDto {
            return VehicleDto(
                id = json.optString("id", ""),
                businessId = json.optString("business_id", ""),
                name = json.optString("name", ""),
                numberPlate = json.optString("number_plate", ""),
                type = json.optString("type", "Truck"),
                notes = json.optString("notes", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class DriverDto(
    val id: String,
    val businessId: String,
    val name: String,
    val phone: String,
    val paymentType: String,
    val rate: Double,
    val assignedVehicleId: String? = null,
    val notes: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("name", name)
        put("phone", phone)
        put("payment_type", paymentType)
        put("rate", rate)
        if (assignedVehicleId != null && assignedVehicleId.isNotBlank()) {
            put("assigned_vehicle_id", assignedVehicleId)
        } else {
            put("assigned_vehicle_id", JSONObject.NULL)
        }
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): DriverDto {
            return DriverDto(
                id = json.optString("id", ""),
                businessId = json.optString("business_id", ""),
                name = json.optString("name", ""),
                phone = json.optString("phone", ""),
                paymentType = json.optString("payment_type", "per_trip"),
                rate = json.optDouble("rate", 0.0),
                assignedVehicleId = if (json.has("assigned_vehicle_id") && !json.isNull("assigned_vehicle_id")) json.optString("assigned_vehicle_id") else null,
                notes = json.optString("notes", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class BookingDto(
    val id: String,
    val businessId: String,
    val vehicleId: String,
    val driverId: String? = null,
    val date: Long,
    val customerRoute: String,
    val amount: Double,
    val paymentStatus: String,
    val notes: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("vehicle_id", vehicleId)
        if (driverId != null && driverId.isNotBlank()) {
            put("driver_id", driverId)
        } else {
            put("driver_id", JSONObject.NULL)
        }
        put("date", date)
        put("customer_route", customerRoute)
        put("amount", amount)
        put("payment_status", paymentStatus)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): BookingDto {
            return BookingDto(
                id = json.optString("id", ""),
                businessId = json.optString("business_id", ""),
                vehicleId = json.optString("vehicle_id", ""),
                driverId = if (json.has("driver_id") && !json.isNull("driver_id")) json.optString("driver_id") else null,
                date = json.optLong("date", System.currentTimeMillis()),
                customerRoute = json.optString("customer_route", ""),
                amount = json.optDouble("amount", 0.0),
                paymentStatus = json.optString("payment_status", "PENDING"),
                notes = json.optString("notes", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class ExpenseDto(
    val id: String,
    val businessId: String,
    val vehicleId: String,
    val driverId: String? = null,
    val category: String,
    val amount: Double,
    val date: Long,
    val notes: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("vehicle_id", vehicleId)
        if (driverId != null && driverId.isNotBlank()) {
            put("driver_id", driverId)
        } else {
            put("driver_id", JSONObject.NULL)
        }
        put("category", category)
        put("amount", amount)
        put("date", date)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): ExpenseDto {
            return ExpenseDto(
                id = json.optString("id", ""),
                businessId = json.optString("business_id", ""),
                vehicleId = json.optString("vehicle_id", ""),
                driverId = if (json.has("driver_id") && !json.isNull("driver_id")) json.optString("driver_id") else null,
                category = json.optString("category", "Fuel"),
                amount = json.optDouble("amount", 0.0),
                date = json.optLong("date", System.currentTimeMillis()),
                notes = json.optString("notes", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

data class DriverPaymentDto(
    val id: String,
    val businessId: String,
    val driverId: String,
    val vehicleId: String? = null,
    val amount: Double,
    val date: Long,
    val notes: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("driver_id", driverId)
        if (vehicleId != null && vehicleId.isNotBlank()) {
            put("vehicle_id", vehicleId)
        } else {
            put("vehicle_id", JSONObject.NULL)
        }
        put("amount", amount)
        put("date", date)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): DriverPaymentDto {
            return DriverPaymentDto(
                id = json.optString("id", ""),
                businessId = json.optString("business_id", ""),
                driverId = json.optString("driver_id", ""),
                vehicleId = if (json.has("vehicle_id") && !json.isNull("vehicle_id")) json.optString("vehicle_id") else null,
                amount = json.optDouble("amount", 0.0),
                date = json.optLong("date", System.currentTimeMillis()),
                notes = json.optString("notes", ""),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

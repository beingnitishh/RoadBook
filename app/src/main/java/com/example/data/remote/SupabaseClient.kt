package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseClient(
    private val defaultBaseUrl: String,
    private val defaultApiKey: String
) {
    private val tag = "SupabaseClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var customBaseUrl: String? = null
    var customApiKey: String? = null

    val baseUrl: String
        get() {
            val url = customBaseUrl?.takeIf { it.isNotBlank() } ?: defaultBaseUrl
            return url.trim().removeSuffix("/")
        }

    val apiKey: String
        get() = customApiKey?.takeIf { it.isNotBlank() } ?: defaultApiKey

    fun isConfigured(): Boolean {
        val url = baseUrl
        val key = apiKey
        return url.isNotBlank() &&
                !url.contains("your-project.supabase.co") &&
                key.isNotBlank() &&
                !key.contains("your-anon-public-key")
    }

    private fun newRequestBuilder(token: String? = null): Request.Builder {
        val builder = Request.Builder()
            .header("apikey", apiKey)
            .header("Content-Type", "application/json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    // ========================================================================
    // AUTHENTICATION
    // ========================================================================

    suspend fun signUp(email: String, password: String, name: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
            val url = "$baseUrl/auth/v1/signup"
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().apply {
                    put("name", name.trim())
                })
            }
            val request = newRequestBuilder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(body).optString("msg", JSONObject(body).optString("error_description", "Sign up failed: HTTP ${response.code}"))
                } catch (e: Exception) {
                    "Sign up failed: HTTP ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(body)
            val session = AuthSession.fromJson(json)
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "signUp error", e)
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
            val url = "$baseUrl/auth/v1/token?grant_type=password"
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val request = newRequestBuilder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMsg = try {
                    val jsonObj = JSONObject(body)
                    jsonObj.optString("error_description", jsonObj.optString("msg", "Invalid credentials"))
                } catch (e: Exception) {
                    "Invalid email or password"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(body)
            val session = AuthSession.fromJson(json)
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "signIn error", e)
            Result.failure(e)
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
            val url = "$baseUrl/auth/v1/token?grant_type=refresh_token"
            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = newRequestBuilder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Session expired"))
            }

            val json = JSONObject(body)
            val session = AuthSession.fromJson(json)
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "refreshSession error", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.success(Unit)
            val url = "$baseUrl/auth/v1/logout"
            val request = newRequestBuilder(token)
                .url(url)
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    // ========================================================================
    // PROFILES & BUSINESSES
    // ========================================================================

    suspend fun getProfile(userId: String, token: String): Result<ProfileDto?> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            if (array.length() == 0) {
                Result.success(null)
            } else {
                Result.success(ProfileDto.fromJson(array.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Log.e(tag, "getProfile error", e)
            Result.failure(e)
        }
    }

    suspend fun createProfile(profile: ProfileDto, token: String): Result<ProfileDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles"
            val request = newRequestBuilder(token)
                .header("Prefer", "return=representation")
                .url(url)
                .post(profile.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to create profile: $body"))

            val array = JSONArray(body)
            Result.success(ProfileDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Log.e(tag, "createProfile error", e)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(profile: ProfileDto, token: String): Result<ProfileDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?id=eq.${profile.id}"
            val request = newRequestBuilder(token)
                .header("Prefer", "return=representation")
                .url(url)
                .patch(profile.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to update profile: $body"))

            val array = JSONArray(body)
            Result.success(ProfileDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Log.e(tag, "updateProfile error", e)
            Result.failure(e)
        }
    }

    suspend fun getBusinessById(businessId: String, token: String): Result<BusinessDto?> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/businesses?id=eq.$businessId&select=*"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            if (array.length() == 0) {
                Result.success(null)
            } else {
                Result.success(BusinessDto.fromJson(array.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Log.e(tag, "getBusinessById error", e)
            Result.failure(e)
        }
    }

    suspend fun joinBusinessByCode(joinCode: String, token: String): Result<BusinessDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/rpc/join_business_by_code"
            val payload = JSONObject().apply {
                put("p_join_code", joinCode.trim().uppercase())
            }
            val request = newRequestBuilder(token)
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to join business: $body"))

            val obj = JSONObject(body)
            Result.success(BusinessDto.fromJson(obj))
        } catch (e: Exception) {
            Log.e(tag, "joinBusinessByCode error", e)
            Result.failure(e)
        }
    }

    suspend fun getBusinessByJoinCode(joinCode: String, token: String): Result<BusinessDto?> = withContext(Dispatchers.IO) {
        try {
            val codeClean = joinCode.trim().uppercase()
            val url = "$baseUrl/rest/v1/businesses?join_code=eq.$codeClean&select=*"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            if (array.length() == 0) {
                Result.success(null)
            } else {
                Result.success(BusinessDto.fromJson(array.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Log.e(tag, "getBusinessByJoinCode error", e)
            Result.failure(e)
        }
    }

    suspend fun createBusiness(name: String, joinCode: String, token: String): Result<BusinessDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/rpc/create_business"
            val payload = JSONObject().apply {
                put("p_name", name.trim())
                put("p_join_code", joinCode.trim().uppercase())
            }
            val request = newRequestBuilder(token)
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to create business: $body"))

            val obj = JSONObject(body)
            Result.success(BusinessDto.fromJson(obj))
        } catch (e: Exception) {
            Log.e(tag, "createBusiness error", e)
            Result.failure(e)
        }
    }

    suspend fun getBusinessMembers(businessId: String, token: String): Result<List<ProfileDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?business_id=eq.$businessId&select=*&order=created_at.asc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<ProfileDto>()
            for (i in 0 until array.length()) {
                list.add(ProfileDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(tag, "getBusinessMembers error", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // VEHICLES CRUD
    // ========================================================================

    suspend fun getVehicles(businessId: String, token: String): Result<List<VehicleDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/vehicles?business_id=eq.$businessId&select=*&order=created_at.asc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<VehicleDto>()
            for (i in 0 until array.length()) {
                list.add(VehicleDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertVehicle(vehicle: VehicleDto, token: String): Result<VehicleDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/vehicles"
            val request = newRequestBuilder(token)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .url(url)
                .post(vehicle.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            Result.success(VehicleDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(remoteId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/vehicles?id=eq.$remoteId"
            val request = newRequestBuilder(token).url(url).delete().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========================================================================
    // DRIVERS CRUD
    // ========================================================================

    suspend fun getDrivers(businessId: String, token: String): Result<List<DriverDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/drivers?business_id=eq.$businessId&select=*&order=created_at.asc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<DriverDto>()
            for (i in 0 until array.length()) {
                list.add(DriverDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertDriver(driver: DriverDto, token: String): Result<DriverDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/drivers"
            val request = newRequestBuilder(token)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .url(url)
                .post(driver.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            Result.success(DriverDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDriver(remoteId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/drivers?id=eq.$remoteId"
            val request = newRequestBuilder(token).url(url).delete().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========================================================================
    // BOOKINGS CRUD
    // ========================================================================

    suspend fun getBookings(businessId: String, token: String): Result<List<BookingDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/bookings?business_id=eq.$businessId&select=*&order=date.desc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<BookingDto>()
            for (i in 0 until array.length()) {
                list.add(BookingDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertBooking(booking: BookingDto, token: String): Result<BookingDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/bookings"
            val request = newRequestBuilder(token)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .url(url)
                .post(booking.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            Result.success(BookingDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBooking(remoteId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/bookings?id=eq.$remoteId"
            val request = newRequestBuilder(token).url(url).delete().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========================================================================
    // EXPENSES CRUD
    // ========================================================================

    suspend fun getExpenses(businessId: String, token: String): Result<List<ExpenseDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/expenses?business_id=eq.$businessId&select=*&order=date.desc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<ExpenseDto>()
            for (i in 0 until array.length()) {
                list.add(ExpenseDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertExpense(expense: ExpenseDto, token: String): Result<ExpenseDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/expenses"
            val request = newRequestBuilder(token)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .url(url)
                .post(expense.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            Result.success(ExpenseDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(remoteId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/expenses?id=eq.$remoteId"
            val request = newRequestBuilder(token).url(url).delete().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========================================================================
    // DRIVER PAYMENTS CRUD
    // ========================================================================

    suspend fun getDriverPayments(businessId: String, token: String): Result<List<DriverPaymentDto>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/driver_payments?business_id=eq.$businessId&select=*&order=date.desc"
            val request = newRequestBuilder(token).url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            val list = mutableListOf<DriverPaymentDto>()
            for (i in 0 until array.length()) {
                list.add(DriverPaymentDto.fromJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertDriverPayment(payment: DriverPaymentDto, token: String): Result<DriverPaymentDto> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/driver_payments"
            val request = newRequestBuilder(token)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .url(url)
                .post(payment.toJson().toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))

            val array = JSONArray(body)
            Result.success(DriverPaymentDto.fromJson(array.getJSONObject(0)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDriverPayment(remoteId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/driver_payments?id=eq.$remoteId"
            val request = newRequestBuilder(token).url(url).delete().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

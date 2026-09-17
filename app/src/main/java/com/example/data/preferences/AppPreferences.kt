package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("roadbook_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_INITIAL_SEEDED = "initial_seeded"
        private const val DEFAULT_CURRENCY = "₹"

        private const val KEY_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_USER_ID = "supabase_user_id"
        private const val KEY_USER_EMAIL = "supabase_user_email"
        private const val KEY_USER_NAME = "supabase_user_name"
        private const val KEY_USER_ROLE = "supabase_user_role"
        private const val KEY_BUSINESS_ID = "supabase_business_id"
        private const val KEY_BUSINESS_NAME = "supabase_business_name"
        private const val KEY_BUSINESS_JOIN_CODE = "supabase_business_join_code"
        private const val KEY_DATA_MIGRATED = "supabase_local_data_migrated"
        private const val KEY_CUSTOM_URL = "custom_supabase_url"
        private const val KEY_CUSTOM_KEY = "custom_supabase_key"
    }

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        set(value) = prefs.edit().putString(KEY_CURRENCY, value).apply()

    var isInitialSeeded: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIAL_SEEDED, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    var businessId: String?
        get() = prefs.getString(KEY_BUSINESS_ID, null)
        set(value) = prefs.edit().putString(KEY_BUSINESS_ID, value).apply()

    var businessName: String?
        get() = prefs.getString(KEY_BUSINESS_NAME, null)
        set(value) = prefs.edit().putString(KEY_BUSINESS_NAME, value).apply()

    var businessJoinCode: String?
        get() = prefs.getString(KEY_BUSINESS_JOIN_CODE, null)
        set(value) = prefs.edit().putString(KEY_BUSINESS_JOIN_CODE, value).apply()

    var isLocalDataMigrated: Boolean
        get() = prefs.getBoolean(KEY_DATA_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_DATA_MIGRATED, value).apply()

    var customSupabaseUrl: String?
        get() = prefs.getString(KEY_CUSTOM_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_URL, value).apply()

    var customSupabaseKey: String?
        get() = prefs.getString(KEY_CUSTOM_KEY, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_KEY, value).apply()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .remove(KEY_BUSINESS_ID)
            .remove(KEY_BUSINESS_NAME)
            .remove(KEY_BUSINESS_JOIN_CODE)
            .apply()
    }
}

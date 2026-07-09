package com.example.foresight

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("foresight_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_UPI = "user_upi"
        private const val KEY_USER_JOINED = "user_joined"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_SOUNDS = "sounds_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_DEFAULT_BANK = "default_bank"
        private const val KEY_BALANCE = "user_balance"
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, "Zaid")
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userPhone: String?
        get() = prefs.getString(KEY_USER_PHONE, "+91 98765 43210")
        set(value) = prefs.edit().putString(KEY_USER_PHONE, value).apply()

    var userUpi: String?
        get() = prefs.getString(KEY_USER_UPI, "zaid@fpay")
        set(value) = prefs.edit().putString(KEY_USER_UPI, value).apply()

    var userJoined: String?
        get() = prefs.getString(KEY_USER_JOINED, "Joined June 2026")
        set(value) = prefs.edit().putString(KEY_USER_JOINED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    var isSoundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUNDS, value).apply()

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var defaultBank: String?
        get() = prefs.getString(KEY_DEFAULT_BANK, "HDFC Bank")
        set(value) = prefs.edit().putString(KEY_DEFAULT_BANK, value).apply()

    var balance: Float
        get() = prefs.getFloat(KEY_BALANCE, 25000.0f)
        set(value) = prefs.edit().putFloat(KEY_BALANCE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}

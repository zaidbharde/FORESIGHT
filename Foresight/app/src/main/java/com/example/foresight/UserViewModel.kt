package com.example.foresight

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.foresight.data.auth.FirebaseAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

enum class AppLanguage {
    ENGLISH, HINDI
}

enum class NotificationType {
    PAYMENT, SECURITY, BANK, PROMO
}

data class NotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotificationType,
    val isRead: Boolean = false
)

data class UserProfile(
    val name: String,
    val phone: String,
    val upiId: String,
    val joinedDate: String,
    val avatarRes: Int? = null
)

data class BankAccount(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val accountHolderName: String,
    val accountNumber: String,
    val ifscCode: String,
    val upiId: String,
    val isPrimary: Boolean,
    val balance: Double,
    val type: String = "Savings"
)

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val isBiometricEnabled: Boolean = false,
    val isFaceUnlockEnabled: Boolean = false,
    val isAppLockEnabled: Boolean = true,
    val isAutoLockEnabled: Boolean = true,
    val isAiProtectionEnabled: Boolean = true,
    val isPaymentSoundsEnabled: Boolean = true,
    val isNotificationsEnabled: Boolean = true
)

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("foresight_prefs", Context.MODE_PRIVATE)
    private val authManager = FirebaseAuthManager()

    private val _isLoggedIn = MutableStateFlow(authManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = prefs.getString("user_name", "Zaid Bharde") ?: "Zaid Bharde",
            phone = authManager.getCurrentUserPhoneNumber() ?: prefs.getString("user_phone", "+91 98765 43210") ?: "+91 98765 43210",
            upiId = prefs.getString("user_upi", "zaid@oksbi") ?: "zaid@oksbi",
            joinedDate = prefs.getString("joined_date", "May 2024") ?: "May 2024"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _bankAccounts = MutableStateFlow<List<BankAccount>>(emptyList())
    val bankAccounts: StateFlow<List<BankAccount>> = _bankAccounts.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _settings = MutableStateFlow(
        AppSettings(
            theme = AppTheme.entries.firstOrNull { it.name == (prefs.getString("app_theme", AppTheme.DARK.name) ?: AppTheme.DARK.name) }
                ?: AppTheme.DARK,
            language = AppLanguage.entries.firstOrNull { it.name == (prefs.getString("app_lang", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name) }
                ?: AppLanguage.ENGLISH,
            isBiometricEnabled = prefs.getBoolean("biometric", false),
            isFaceUnlockEnabled = prefs.getBoolean("face_unlock", false),
            isAppLockEnabled = prefs.getBoolean("app_lock", true),
            isAutoLockEnabled = prefs.getBoolean("auto_lock", true),
            isAiProtectionEnabled = prefs.getBoolean("ai_protection", true),
            isPaymentSoundsEnabled = prefs.getBoolean("sounds", true),
            isNotificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        migrateLegacyPreferences()
        loadBankAccounts()
        loadNotifications()
    }

    private fun migrateLegacyPreferences() {
        val legacyValue = prefs.all["notifications"]
        if (legacyValue != null) {
            val editor = prefs.edit()
            if (legacyValue is String) {
                // It was the notification list JSON
                editor.putString("notifications_list", legacyValue)
            } else if (legacyValue is Boolean) {
                // It was the settings toggle
                editor.putBoolean("notifications_enabled", legacyValue)
            }
            editor.remove("notifications")
            editor.apply()
        }
    }

    private fun loadBankAccounts() {
        val json = prefs.getString("bank_accounts", null)
        if (json != null) {
            val parsed = runCatching {
                val list = mutableListOf<BankAccount>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BankAccount(
                            id = obj.getString("id"),
                            bankName = obj.getString("bankName"),
                            accountHolderName = obj.getString("accountHolderName"),
                            accountNumber = obj.getString("accountNumber"),
                            ifscCode = obj.getString("ifscCode"),
                            upiId = obj.getString("upiId"),
                            isPrimary = obj.getBoolean("isPrimary"),
                            balance = obj.getDouble("balance"),
                            type = obj.getString("type")
                        )
                    )
                }
                list
            }.getOrElse { emptyList() }

            if (parsed.isNotEmpty()) {
                _bankAccounts.value = parsed
                return
            }
        } else {
            // no-op, falls through to default initialization below
        }

        val defaultBank = BankAccount(
            bankName = "SBI",
            accountHolderName = "Zaid Bharde",
            accountNumber = "XXXX XXXX 4321",
            ifscCode = "SBIN0001234",
            upiId = "zaid@oksbi",
            isPrimary = true,
            balance = 25000.0
        )
        _bankAccounts.value = listOf(defaultBank)
        saveBankAccounts(listOf(defaultBank))
    }

    private fun saveBankAccounts(list: List<BankAccount>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("bankName", it.bankName)
                put("accountHolderName", it.accountHolderName)
                put("accountNumber", it.accountNumber)
                put("ifscCode", it.ifscCode)
                put("upiId", it.upiId)
                put("isPrimary", it.isPrimary)
                put("balance", it.balance)
                put("type", it.type)
            }
            array.put(obj)
        }
        prefs.edit().putString("bank_accounts", array.toString()).apply()
    }

    private fun loadNotifications() {
        val json = prefs.getString("notifications_list", null)
        if (json != null) {
            val parsed = runCatching {
                val list = mutableListOf<NotificationItem>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = NotificationType.entries.firstOrNull { it.name == obj.getString("type") } ?: NotificationType.SECURITY
                    list.add(
                        NotificationItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            timestamp = obj.getLong("timestamp"),
                            type = type,
                            isRead = obj.getBoolean("isRead")
                        )
                    )
                }
                list
            }.getOrElse { emptyList() }

            if (parsed.isNotEmpty()) {
                _notifications.value = parsed
                return
            }
        }

        addNotification(
            "Welcome to Foresight!",
            "Your AI-powered security shield is now active. Explore the next gen fintech.",
            NotificationType.SECURITY
        )
    }

    private fun saveNotifications(list: List<NotificationItem>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("description", it.description)
                put("timestamp", it.timestamp)
                put("type", it.type.name)
                put("isRead", it.isRead)
            }
            array.put(obj)
        }
        prefs.edit().putString("notifications_list", array.toString()).apply()
    }

    fun addNotification(title: String, description: String, type: NotificationType) {
        val newItem = NotificationItem(title = title, description = description, type = type)
        val updated = listOf(newItem) + _notifications.value
        _notifications.value = updated.take(50) // Keep last 50
        saveNotifications(_notifications.value)
    }

    fun markAllAsRead() {
        val updated = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = updated
        saveNotifications(updated)
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
        saveNotifications(emptyList())
    }

    fun login(phone: String) {
        _isLoggedIn.value = true
        _userProfile.value = _userProfile.value.copy(phone = phone)
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_phone", phone)
            apply()
        }
        addNotification("Login Successful", "You have successfully logged in from a new device.", NotificationType.SECURITY)
    }

    fun logout() {
        authManager.logout()
        _isLoggedIn.value = false
        prefs.edit().putBoolean("is_logged_in", false).apply()
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putString("app_theme", newSettings.theme.name)
            putString("app_lang", newSettings.language.name)
            putBoolean("biometric", newSettings.isBiometricEnabled)
            putBoolean("face_unlock", newSettings.isFaceUnlockEnabled)
            putBoolean("app_lock", newSettings.isAppLockEnabled)
            putBoolean("auto_lock", newSettings.isAutoLockEnabled)
            putBoolean("ai_protection", newSettings.isAiProtectionEnabled)
            putBoolean("sounds", newSettings.isPaymentSoundsEnabled)
            putBoolean("notifications_enabled", newSettings.isNotificationsEnabled)
            apply()
        }
    }

    fun setTheme(theme: AppTheme) {
        updateSettings(_settings.value.copy(theme = theme))
    }

    fun setLanguage(lang: AppLanguage) {
        updateSettings(_settings.value.copy(language = lang))
    }

    fun setPrimaryBank(bankId: String) {
        val updated = _bankAccounts.value.map {
            it.copy(isPrimary = it.id == bankId)
        }
        _bankAccounts.value = updated
        saveBankAccounts(updated)
        val bank = updated.find { it.id == bankId }
        addNotification("Primary Bank Updated", "${bank?.bankName} is now your default payment source.", NotificationType.BANK)
    }

    fun addBank(bankName: String, holderName: String, number: String, ifsc: String, upi: String, openingBalance: Double) {
        val maskedNumber = if (number.length >= 4 && !number.startsWith("XXXX")) {
             "XXXX XXXX " + number.takeLast(4)
        } else {
            number
        }
        val newBank = BankAccount(
            bankName = bankName,
            accountHolderName = holderName,
            accountNumber = maskedNumber,
            ifscCode = ifsc,
            upiId = upi,
            isPrimary = _bankAccounts.value.isEmpty(),
            balance = openingBalance
        )
        val updated = _bankAccounts.value + newBank
        _bankAccounts.value = updated
        saveBankAccounts(updated)
        addNotification("Bank Linked", "$bankName account linked successfully.", NotificationType.BANK)
    }

    fun updateBank(id: String, bankName: String, holderName: String, number: String, ifsc: String, upi: String, balance: Double) {
        val maskedNumber = if (number.length >= 4 && !number.startsWith("XXXX")) {
            "XXXX XXXX " + number.takeLast(4)
        } else {
            number
        }
        val updated = _bankAccounts.value.map {
            if (it.id == id) {
                it.copy(
                    bankName = bankName,
                    accountHolderName = holderName,
                    accountNumber = maskedNumber,
                    ifscCode = ifsc,
                    upiId = upi,
                    balance = balance
                )
            } else it
        }
        _bankAccounts.value = updated
        saveBankAccounts(updated)
    }

    fun removeBank(bankId: String) {
        val current = _bankAccounts.value
        val bankToRemove = current.find { it.id == bankId }
        val updated = current.filter { it.id != bankId }
        
        if (current.find { it.id == bankId }?.isPrimary == true && updated.isNotEmpty()) {
            val newList = updated.toMutableList()
            newList[0] = newList[0].copy(isPrimary = true)
            _bankAccounts.value = newList
            saveBankAccounts(newList)
        } else {
            _bankAccounts.value = updated
            saveBankAccounts(updated)
        }
        addNotification("Bank Removed", "${bankToRemove?.bankName} account has been unlinked.", NotificationType.BANK)
    }

    fun deductFromPrimary(amount: Double): Boolean {
        val current = _bankAccounts.value
        val primary = current.find { it.isPrimary } ?: return false
        if (primary.balance < amount) return false

        val updated = current.map {
            if (it.isPrimary) it.copy(balance = it.balance - amount) else it
        }
        _bankAccounts.value = updated
        saveBankAccounts(updated)
        
        addNotification("Payment Sent", "₹$amount successfully paid from ${primary.bankName}.", NotificationType.PAYMENT)
        return true
    }
}

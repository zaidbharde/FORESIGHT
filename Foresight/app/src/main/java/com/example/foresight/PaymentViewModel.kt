package com.example.foresight

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foresight.data.network.PredictionRequest
import com.example.foresight.data.network.PredictionResponse
import com.example.foresight.data.repository.RiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

enum class TransactionType {
    SENT, RECEIVED
}

data class Transaction(
    val id: String,
    val name: String,
    val initial: String,
    val amount: String,
    val date: String,
    val time: String,
    val status: String,
    val color: Color,
    val bankName: String = "SBI",
    val type: TransactionType = TransactionType.SENT,
    val timestamp: Long = System.currentTimeMillis(),
    // AI Risk Data
    val riskScore: Float = 0f,
    val riskLevel: String = "Low",
    val confidence: Float = 1.0f,
    val recommendation: String = "Safe to proceed",
    val predictionTimeMs: Float = 0f,
    val reasons: List<String> = emptyList()
)

data class ForesightContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val color: Color,
    val lastInteraction: Long = 0,
    val isTrusted: Boolean = false,
    val isDemo: Boolean = false,
    val upiId: String = ""
)

sealed class RiskState {
    object Idle : RiskState()
    object Loading : RiskState()
    data class Success(val response: PredictionResponse) : RiskState()
    data class Error(val message: String) : RiskState()
}

class PaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("foresight_payments", Context.MODE_PRIVATE)
    private val repository = RiskRepository()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _contacts = MutableStateFlow<List<ForesightContact>>(emptyList())
    val contacts: StateFlow<List<ForesightContact>> = _contacts.asStateFlow()

    private val _riskState = MutableStateFlow<RiskState>(RiskState.Idle)
    val riskState: StateFlow<RiskState> = _riskState.asStateFlow()

    private val _currentPrediction = MutableStateFlow<PredictionResponse?>(null)
    val currentPrediction: StateFlow<PredictionResponse?> = _currentPrediction.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val transJson = prefs.getString("transactions", null)
        if (transJson != null) {
            val parsedTransactions = runCatching {
                val list = mutableListOf<Transaction>()
                val array = JSONArray(transJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val reasonsArray = obj.optJSONArray("reasons")
                    val reasons = mutableListOf<String>()
                    if (reasonsArray != null) {
                        for (j in 0 until reasonsArray.length()) {
                            reasons.add(reasonsArray.getString(j))
                        }
                    }

                    list.add(
                        Transaction(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            initial = obj.getString("initial"),
                            amount = obj.getString("amount"),
                            date = obj.getString("date"),
                            time = obj.getString("time"),
                            status = obj.getString("status"),
                            color = Color(obj.getInt("color")),
                            bankName = obj.optString("bankName", "SBI"),
                            type = TransactionType.valueOf(obj.getString("type")),
                            timestamp = obj.getLong("timestamp"),
                            riskScore = obj.optDouble("riskScore", 0.0).toFloat(),
                            riskLevel = obj.optString("riskLevel", "Low"),
                            confidence = obj.optDouble("confidence", 1.0).toFloat(),
                            recommendation = obj.optString("recommendation", "Safe to proceed"),
                            predictionTimeMs = obj.optDouble("predictionTimeMs", 0.0).toFloat(),
                            reasons = reasons
                        )
                    )
                }
                list
            }.getOrElse { emptyList() }

            _transactions.value = parsedTransactions
        } else {
            _transactions.value = listOf(
                Transaction("TXN001", "Aamir", "A", "500", "Today", "12:41 PM", "Safe", Color(0xFFFF6D6D), "SBI", timestamp = System.currentTimeMillis() - 3600000),
                Transaction("TXN002", "XYZ Store", "X", "1200", "Yesterday", "08:20 PM", "Safe", Color(0xFF20E3B2), "SBI", timestamp = System.currentTimeMillis() - 86400000),
                Transaction("TXN003", "Tabish", "T", "2450", "2 May", "10:15 PM", "Safe", Color(0xFF7C4DFF), "SBI", timestamp = System.currentTimeMillis() - 172800000),
                Transaction("TXN004", "Nimra", "N", "1500", "1 May", "11:00 AM", "Safe", Color(0xFFFF5C8A), "SBI", TransactionType.RECEIVED, timestamp = System.currentTimeMillis() - 259200000)
            )
            saveTransactions(_transactions.value)
        }

        val contactsJson = prefs.getString("contacts", null)
        if (contactsJson != null) {
            val parsedContacts = runCatching {
                val contactMap = mutableMapOf<String, ForesightContact>()
                val array = JSONArray(contactsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val contact = ForesightContact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        phone = obj.getString("phone"),
                        color = Color(obj.getInt("color")),
                        lastInteraction = obj.getLong("lastInteraction"),
                        isTrusted = obj.getBoolean("isTrusted"),
                        isDemo = obj.optBoolean("isDemo", false),
                        upiId = obj.optString("upiId", "")
                    )
                    val comp = normalizeForComparison(contact.phone)
                    if (comp.isEmpty()) continue

                    val existing = contactMap[comp]
                    if (existing == null || (!existing.isTrusted && contact.isTrusted) || (existing.name.isBlank() && contact.name.isNotBlank())) {
                        contactMap[comp] = contact
                    }
                }
                contactMap.values.toList()
            }.getOrElse { emptyList() }

            if (parsedContacts.isNotEmpty()) {
                _contacts.value = parsedContacts
            } else {
                _contacts.value = listOf(
                    ForesightContact(name = "Rahul Patil", phone = "+91 9987654321", color = Color(0xFFFF6D6D), isTrusted = true, isDemo = true, upiId = "rahul@okaxis"),
                    ForesightContact(name = "Priya Mehta", phone = "+91 9876543210", color = Color(0xFF7C4DFF), isTrusted = true, isDemo = true, upiId = "priya@oksbi"),
                    ForesightContact(name = "Aarav Sharma", phone = "+91 9123456789", color = Color(0xFF6D8DFF), isTrusted = false, isDemo = true, upiId = "aarav@okhdfcbank"),
                    ForesightContact(name = "Sneha Kapoor", phone = "+91 9876543211", color = Color(0xFFFF5C8A), isTrusted = false, isDemo = true, upiId = "sneha@okicici"),
                    ForesightContact(name = "Vikram Singh", phone = "+91 9988776655", color = Color(0xFF20E3B2), isTrusted = false, isDemo = true, upiId = "vikram@okhdfcbank")
                )
            }
        } else {
            // Default common contacts for the demo
            val defaults = listOf(
                ForesightContact(name = "Rahul Patil", phone = "+91 9987654321", color = Color(0xFFFF6D6D), isTrusted = true, isDemo = true, upiId = "rahul@okaxis"),
                ForesightContact(name = "Priya Mehta", phone = "+91 9876543210", color = Color(0xFF7C4DFF), isTrusted = true, isDemo = true, upiId = "priya@oksbi"),
                ForesightContact(name = "Aarav Sharma", phone = "+91 9123456789", color = Color(0xFF6D8DFF), isTrusted = false, isDemo = true, upiId = "aarav@okhdfcbank"),
                ForesightContact(name = "Sneha Kapoor", phone = "+91 9876543211", color = Color(0xFFFF5C8A), isTrusted = false, isDemo = true, upiId = "sneha@okicici"),
                ForesightContact(name = "Vikram Singh", phone = "+91 9988776655", color = Color(0xFF20E3B2), isTrusted = false, isDemo = true, upiId = "vikram@okhdfcbank")
            )
            _contacts.value = defaults
            // Don't save defaults to prefs, keep them as transient fallback
        }
    }

    private fun normalizeForComparison(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private fun saveTransactions(list: List<Transaction>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("initial", it.initial)
                put("amount", it.amount)
                put("date", it.date)
                put("time", it.time)
                put("status", it.status)
                put("color", it.color.toArgb())
                put("bankName", it.bankName)
                put("type", it.type.name)
                put("timestamp", it.timestamp)
                put("riskScore", it.riskScore.toDouble())
                put("riskLevel", it.riskLevel)
                put("confidence", it.confidence.toDouble())
                put("recommendation", it.recommendation)
                put("predictionTimeMs", it.predictionTimeMs.toDouble())
                val reasonsArray = JSONArray()
                it.reasons.forEach { reason -> reasonsArray.put(reason) }
                put("reasons", reasonsArray)
            }
            array.put(obj)
        }
        prefs.edit().putString("transactions", array.toString()).apply()
    }

    private fun saveContacts(list: List<ForesightContact>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("phone", it.phone)
                put("color", it.color.toArgb())
                put("lastInteraction", it.lastInteraction)
                put("isTrusted", it.isTrusted)
                put("isDemo", it.isDemo)
                put("upiId", it.upiId)
            }
            array.put(obj)
        }
        prefs.edit().putString("contacts", array.toString()).apply()
    }

    fun setContacts(contacts: List<ForesightContact>) {
        // If we are setting real contacts, we should remove demo contacts first
        val current = _contacts.value.filter { !it.isDemo }
        val mergedMap = mutableMapOf<String, ForesightContact>()
        
        current.forEach { 
            val comp = normalizeForComparison(it.phone)
            if (comp.isNotEmpty()) mergedMap[comp] = it 
        }
        
        contacts.forEach { 
            val comp = normalizeForComparison(it.phone)
            if (comp.isNotEmpty()) {
                val existing = mergedMap[comp]
                if (existing == null) {
                    mergedMap[comp] = it
                } else {
                    if (existing.name.isBlank() || existing.name.all { c -> c.isDigit() || c == '+' || c == ' ' }) {
                        if (it.name.isNotBlank() && !it.name.all { c -> c.isDigit() || c == '+' || c == ' ' }) {
                            mergedMap[comp] = existing.copy(name = it.name)
                        }
                    }
                }
            }
        }
        
        val newList = mergedMap.values.toList()
        
        if (newList.isEmpty()) {
            // If no real contacts, fall back to demo
            val defaults = listOf(
                ForesightContact(name = "Rahul Patil", phone = "+91 9987654321", color = Color(0xFFFF6D6D), isTrusted = true, isDemo = true, upiId = "rahul@okaxis"),
                ForesightContact(name = "Priya Mehta", phone = "+91 9876543210", color = Color(0xFF7C4DFF), isTrusted = true, isDemo = true, upiId = "priya@oksbi"),
                ForesightContact(name = "Aarav Sharma", phone = "+91 9123456789", color = Color(0xFF6D8DFF), isTrusted = false, isDemo = true, upiId = "aarav@okhdfcbank"),
                ForesightContact(name = "Sneha Kapoor", phone = "+91 9876543211", color = Color(0xFFFF5C8A), isTrusted = false, isDemo = true, upiId = "sneha@okicici"),
                ForesightContact(name = "Vikram Singh", phone = "+91 9988776655", color = Color(0xFF20E3B2), isTrusted = false, isDemo = true, upiId = "vikram@okhdfcbank")
            )
            _contacts.value = defaults
        } else {
            _contacts.value = newList
            saveContacts(newList)
        }
    }

    fun toggleTrusted(contactId: String) {
        val updated = _contacts.value.map {
            if (it.id == contactId) it.copy(isTrusted = !it.isTrusted) else it
        }
        _contacts.value = updated
        saveContacts(updated)
    }

    fun analyzeRisk(amount: String, isTrusted: Boolean, contactPhone: String = "") {
        val parsedAmount = amount.toFloatOrNull()
        if (parsedAmount == null || !parsedAmount.isFinite() || parsedAmount <= 0f || parsedAmount > MAX_ALLOWED_AMOUNT) {
            _riskState.value = RiskState.Error("Invalid amount for risk analysis.")
            return
        }

        _riskState.value = RiskState.Loading
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Derive contextual data for the hybrid engine
            val transactionsLastHour = _transactions.value.count { it.timestamp > System.currentTimeMillis() - 3600000 }
            val transactionsLast24h = _transactions.value.count { it.timestamp > System.currentTimeMillis() - 86400000 }
            
            val contact = if (contactPhone.isNotEmpty()) {
                val comp = normalizeForComparison(contactPhone)
                _contacts.value.find { normalizeForComparison(it.phone) == comp }
            } else null
            
            val isFirstTime = contact?.lastInteraction == 0L
            val accountHistoryDays = if (contact?.isDemo == true) 365 else 10 // Demo accounts are "old"
            
            val request = PredictionRequest(
                amount = parsedAmount,
                trustedContact = isTrusted,
                newDevice = false,
                locationAnomaly = false,
                hour = hour,
                transactionsLastHour = transactionsLastHour,
                transactionsLast24h = transactionsLast24h,
                simRecentlyChanged = false,
                activeCall = false,
                deviceAnomaly = false,
                accountHistoryDays = accountHistoryDays,
                firstTimeBeneficiary = isFirstTime
            )

            val result = repository.getRiskPrediction(request)
            result.onSuccess {
                _currentPrediction.value = it
                _riskState.value = RiskState.Success(it)
            }.onFailure {
                _riskState.value = RiskState.Error(it.message ?: "Unknown error")
            }
        }
    }

    fun resetRiskState() {
        _riskState.value = RiskState.Idle
    }

    fun performPayment(
        name: String, 
        initial: String, 
        amountStr: String, 
        note: String, 
        color: Color, 
        bankName: String,
        prediction: PredictionResponse? = null
    ) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val now = Date()

        val newTransaction = Transaction(
            id = "FPAY${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}",
            name = name,
            initial = initial,
            amount = amountStr,
            date = "Today",
            time = timeFormat.format(now),
            status = prediction?.finalRisk ?: prediction?.riskLevel ?: "Safe",
            color = color,
            bankName = bankName,
            type = TransactionType.SENT,
            timestamp = System.currentTimeMillis(),
            riskScore = prediction?.adjustedScore ?: prediction?.riskScore ?: 0f,
            riskLevel = prediction?.finalRisk ?: prediction?.riskLevel ?: "Low",
            confidence = prediction?.confidence ?: 1.0f,
            recommendation = prediction?.finalRecommendation
                ?: prediction?.recommendation
                ?: "Safe to proceed",
            predictionTimeMs = prediction?.predictionTimeMs ?: 0f,
            reasons = prediction?.reasons ?: emptyList()
        )

        _transactions.value = (listOf(newTransaction) + _transactions.value).sortedByDescending { it.timestamp }
        saveTransactions(_transactions.value)
        
        _contacts.value = _contacts.value.map {
            if (it.name == name) it.copy(lastInteraction = System.currentTimeMillis()) else it
        }
        saveContacts(_contacts.value)
    }

    private companion object {
        const val MAX_ALLOWED_AMOUNT = 1_000_000f
    }
}

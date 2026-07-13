package com.example.foresight.data.network

import com.google.gson.annotations.SerializedName

data class PredictionRequest(
    @SerializedName("amount") val amount: Float,
    @SerializedName("trusted_contact") val trustedContact: Boolean,
    @SerializedName("new_device") val newDevice: Boolean,
    @SerializedName("location_anomaly") val locationAnomaly: Boolean,
    @SerializedName("hour") val hour: Int,
    @SerializedName("transactions_last_hour") val transactionsLastHour: Int,
    @SerializedName("transactions_last_24h") val transactionsLast24h: Int,
    @SerializedName("sim_recently_changed") val simRecentlyChanged: Boolean = false,
    @SerializedName("active_call") val activeCall: Boolean = false,
    @SerializedName("device_anomaly") val deviceAnomaly: Boolean = false,
    @SerializedName("account_history_days") val accountHistoryDays: Int = 0,
    @SerializedName("first_time_beneficiary") val firstTimeBeneficiary: Boolean = false
)

data class PredictionResponse(
    @SerializedName("risk_score") val riskScore: Float,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("reasons") val reasons: List<String>,
    @SerializedName("recommendation") val recommendation: String,
    @SerializedName("prediction_time_ms") val predictionTimeMs: Float,
    @SerializedName("ai_score") val aiScore: Float? = null,
    @SerializedName("adjusted_score") val adjustedScore: Float? = null,
    @SerializedName("applied_rules") val appliedRules: List<AppliedRuleResponse>? = null,
    @SerializedName("final_risk") val finalRisk: String? = null,
    @SerializedName("final_recommendation") val finalRecommendation: String? = null,
    @SerializedName("rule_config_version") val ruleConfigVersion: String? = null
)

data class AppliedRuleResponse(
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("rule_name") val ruleName: String,
    @SerializedName("configured_adjustment") val configuredAdjustment: Float,
    @SerializedName("adjustment") val adjustment: Float,
    @SerializedName("explanation") val explanation: String,
    @SerializedName("score_before") val scoreBefore: Float,
    @SerializedName("score_after") val scoreAfter: Float
)

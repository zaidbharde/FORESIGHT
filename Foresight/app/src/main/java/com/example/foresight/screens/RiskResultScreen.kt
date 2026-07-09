package com.example.foresight.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.UpiAppSelectorSheet
import com.example.foresight.UpiManager
import com.example.foresight.UpiResult
import com.example.foresight.data.network.PredictionResponse

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentMint = Color(0xFF20E3B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskResultScreen(
    prediction: PredictionResponse,
    contactName: String,
    amount: String,
    onPaymentSuccess: () -> Unit,
    onCancelClick: () -> Unit,
    payeeVpa: String,
    transactionNote: String
) {
    val context = LocalContext.current
    val finalScore = prediction.adjustedScore ?: prediction.riskScore
    val aiScore = prediction.aiScore ?: prediction.riskScore
    val finalRisk = prediction.finalRisk ?: prediction.riskLevel
    val finalRecommendation = prediction.finalRecommendation ?: prediction.recommendation
    val appliedRules = prediction.appliedRules.orEmpty()
    val status = finalRisk.lowercase()
    
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAppSheet by remember { mutableStateOf(false) }
    
    val upiApps = remember(context) {
        UpiManager.getInstalledUpiApps(context)
    }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data?.getStringExtra("response")
        val resultStatus = UpiManager.parseUpiResponse(data)
        
        when (resultStatus) {
            UpiResult.SUCCESS -> onPaymentSuccess()
            UpiResult.FAILED -> Toast.makeText(context, "Payment Failed", Toast.LENGTH_SHORT).show()
            UpiResult.CANCELLED -> Toast.makeText(context, "Payment Cancelled", Toast.LENGTH_SHORT).show()
            UpiResult.UNKNOWN -> Toast.makeText(
                context, 
                "Payment status could not be verified. Please verify inside your UPI application.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val config = when (status) {
        "safe", "low" -> ResultConfig(
            title = "Transaction Safe",
            icon = Icons.Default.Check,
            color = AccentMint,
            actionText = "Proceed to Pay"
        )
        "warning", "medium" -> ResultConfig(
            title = "Medium Risk Detected",
            icon = Icons.Default.PriorityHigh,
            color = Color(0xFFFFC857),
            actionText = "Cancel Payment"
        )
        "high" -> ResultConfig(
            title = "High Risk Detected",
            icon = Icons.Default.Close,
            color = Color(0xFFFF6B6B),
            actionText = "Cancel Payment"
        )
        else -> ResultConfig(
            title = "Critical Risk Detected",
            icon = Icons.Default.Close,
            color = Color(0xFFFF6B6B),
            actionText = "Cancel Payment"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(config.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.color,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = config.title,
            color = config.color,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "₹ $amount to $contactName",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.05f)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RiskMetric(
                        label = "Final Risk Score",
                        value = "${finalScore.toInt()}/100",
                        valueColor = config.color,
                        modifier = Modifier.weight(1f)
                    )
                    RiskMetric(
                        label = "Confidence",
                        value = String.format(
                            java.util.Locale.US,
                            "%.2f%%",
                            prediction.confidence
                        ),
                        valueColor = Color.White,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hybrid AI Decision Details",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (detailsExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (detailsExpanded) "Collapse" else "Expand",
                            tint = config.color
                        )
                    }
                }

                if (detailsExpanded) {
                    Spacer(modifier = Modifier.height(20.dp))
                    DecisionSectionTitle("AI Analysis", config.color)
                    DecisionText("XGBoost AI score: ${aiScore.toInt()}/100")
                    DecisionText(
                        String.format(
                            java.util.Locale.US,
                            "Model confidence: %.2f%%",
                            prediction.confidence
                        )
                    )
                    DecisionText("Statistical model output before business-rule adjustments.")

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(20.dp))
                    DecisionSectionTitle("Business Rules Applied", config.color)
                    if (appliedRules.isEmpty()) {
                        DecisionText("No contextual rule changed the AI score.")
                    } else {
                        appliedRules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.ruleName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = rule.explanation,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%+.0f",
                                        rule.adjustment
                                    ),
                                    color = if (rule.adjustment < 0f) {
                                        AccentMint
                                    } else {
                                        Color(0xFFFFC857)
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(20.dp))
                    DecisionSectionTitle("Final Decision", config.color)
                    DecisionText("$finalRisk · ${finalScore.toInt()}/100")
                    DecisionText(finalRecommendation)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "Prediction Time: %.1fms",
                        prediction.predictionTimeMs
                    ),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (status == "safe" || status == "low") {
                    if (upiApps.isEmpty()) {
                        showNoUpiDialog(context)
                    } else {
                        showAppSheet = true
                    }
                } else {
                    onCancelClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = config.color)
        ) {
            Text(
                config.actionText,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (status != "safe" && status != "low") {
            TextButton(
                onClick = {
                    if (upiApps.isEmpty()) {
                        showNoUpiDialog(context)
                    } else {
                        showAppSheet = true
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Proceed Anyway", color = Color.White.copy(alpha = 0.6f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAppSheet) {
        UpiAppSelectorSheet(
            upiApps = upiApps,
            onAppSelected = { app ->
                showAppSheet = false
                UpiManager.launchUpiApp(
                    context = context,
                    app = app,
                    payeeVpa = payeeVpa,
                    payeeName = contactName,
                    amount = amount,
                    transactionNote = transactionNote,
                    launcher = upiLauncher
                )
            },
            onDismiss = { showAppSheet = false }
        )
    }
}

private fun showNoUpiDialog(context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle("No UPI App Found")
        .setMessage("Please install a UPI payment application (Google Pay, PhonePe, Paytm, etc.) to continue.")
        .setPositiveButton("OK", null)
        .show()
}

@Composable
private fun DecisionSectionTitle(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DecisionText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun RiskMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class ResultConfig(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val actionText: String
)

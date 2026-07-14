package com.example.foresight.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foresight.UpiAppSelectorSheet
import com.example.foresight.UpiManager
import com.example.foresight.UpiResult
import com.example.foresight.data.network.PredictionResponse
import com.example.foresight.ui.components.*
import com.example.foresight.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskResultScreen(
    prediction: PredictionResponse,
    contactName: String,
    amount: String,
    onPaymentSuccess: () -> Unit,
    onCancelClick: () -> Unit,
    payeeVpa: String,
    transactionNote: String,
    mc: String? = null,
    tr: String? = null,
    tid: String? = null,
    mode: String? = null,
    orgid: String? = null,
    sign: String? = null,
    url: String? = null,
    purpose: String? = null,
    mam: String? = null
) {
    val context = LocalContext.current
    val finalScore = prediction.adjustedScore ?: prediction.riskScore
    val aiScore = prediction.aiScore ?: prediction.riskScore
    val finalRisk = prediction.finalRisk ?: prediction.riskLevel
    val appliedRules = prediction.appliedRules.orEmpty()
    
    val riskTier = riskTierFromLabel(finalRisk)
    val riskPres = rememberRiskPresentation(riskTier)
    
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

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { -it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FSStatusBadge(
                        icon = riskPres.icon,
                        tint = riskPres.color
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = riskPres.label.uppercase(),
                        color = riskPres.color,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = com.example.foresight.ui.theme.ForesightTypography.headlineMedium.letterSpacing
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "₹ $amount to $contactName",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 2 }
            ) {
                Column {
                    FSHeroCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetricTile(
                                    label = "Risk Score",
                                    value = "${finalScore.toInt()}%",
                                    valueColor = riskPres.color,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    label = "Confidence",
                                    value = String.format(java.util.Locale.US, "%.0f%%", prediction.confidence),
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                )
                                MetricTile(
                                    label = "Time",
                                    value = "${prediction.predictionTimeMs.toInt()}ms",
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = ExtendedTheme.colors.cardBorder)
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
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = riskPres.color
                                    )
                                }
                            }

                            if (detailsExpanded) {
                                Spacer(modifier = Modifier.height(16.dp))
                                DecisionSection("AI Analysis", riskPres.color) {
                                    DecisionRow("XGBoost AI Score", "${aiScore.toInt()}/100")
                                    DecisionRow("Model Confidence", String.format(java.util.Locale.US, "%.2f%%", prediction.confidence))
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                DecisionSection("Business Rules", riskPres.color) {
                                    if (appliedRules.isEmpty()) {
                                        Text(
                                            "No contextual rules triggered.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        appliedRules.forEach { rule ->
                                            RuleItem(rule.ruleName, rule.explanation, rule.adjustment)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Recommendation Card
                    FSCard(
                        containerColor = riskPres.color.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, riskPres.color.copy(alpha = 0.2f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = riskPres.icon,
                                contentDescription = null,
                                tint = riskPres.color,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = prediction.finalRecommendation ?: prediction.recommendation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(40.dp))

            FSPrimaryButton(
                text = if (riskTier == RiskTier.SAFE) "Proceed to Pay" else "Cancel Payment",
                containerColor = if (riskTier == RiskTier.SAFE) MaterialTheme.colorScheme.primary else ExtendedTheme.colors.riskHigh,
                onClick = {
                    if (riskTier == RiskTier.SAFE) {
                        if (upiApps.isEmpty()) showNoUpiDialog(context) else showAppSheet = true
                    } else {
                        onCancelClick()
                    }
                }
            )

            if (riskTier != RiskTier.SAFE) {
                Spacer(modifier = Modifier.height(12.dp))
                FSSecondaryButton(
                    text = "Proceed Anyway",
                    onClick = {
                        if (upiApps.isEmpty()) showNoUpiDialog(context) else showAppSheet = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
                    launcher = upiLauncher,
                    merchantCode = mc,
                    transactionRef = tr,
                    transactionId = tid,
                    mode = mode,
                    orgid = orgid,
                    sign = sign,
                    url = url,
                    purpose = purpose,
                    mam = mam
                )
            },
            onDismiss = { showAppSheet = false }
        )
    }
}

@Composable
private fun DecisionSection(title: String, color: Color, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DecisionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RuleItem(name: String, explanation: String, adjustment: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = String.format(java.util.Locale.US, "%+.0f", adjustment),
            style = MaterialTheme.typography.labelLarge,
            color = if (adjustment < 0f) ExtendedTheme.colors.riskSafe else ExtendedTheme.colors.riskMedium
        )
    }
}

private fun showNoUpiDialog(context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle("No UPI App Found")
        .setMessage("Please install a UPI payment application to continue.")
        .setPositiveButton("OK", null)
        .show()
}

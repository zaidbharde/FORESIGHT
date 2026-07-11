package com.example.foresight.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class RiskTier { SAFE, MEDIUM, HIGH, CRITICAL }

fun riskTierFromLabel(label: String): RiskTier = when (label.lowercase()) {
    "safe", "low" -> RiskTier.SAFE
    "warning", "medium" -> RiskTier.MEDIUM
    "high" -> RiskTier.HIGH
    else -> RiskTier.CRITICAL
}

data class RiskPresentation(
    val color: Color,
    val icon: ImageVector,
    val label: String
)

@Composable
fun rememberRiskPresentation(tier: RiskTier): RiskPresentation {
    val colors = ExtendedTheme.colors
    return when (tier) {
        RiskTier.SAFE -> RiskPresentation(colors.riskSafe, Icons.Default.Check, "Safe")
        RiskTier.MEDIUM -> RiskPresentation(colors.riskMedium, Icons.Default.WarningAmber, "Medium Risk")
        RiskTier.HIGH -> RiskPresentation(colors.riskHigh, Icons.Default.PriorityHigh, "High Risk")
        RiskTier.CRITICAL -> RiskPresentation(colors.riskCritical, Icons.Default.Block, "Critical Risk")
    }
}

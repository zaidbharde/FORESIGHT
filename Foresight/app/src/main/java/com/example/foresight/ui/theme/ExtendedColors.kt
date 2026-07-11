package com.example.foresight.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ForesightExtendedColors(
    val riskSafe: Color,
    val riskMedium: Color,
    val riskHigh: Color,
    val riskCritical: Color,
    val surfaceElevated: Color,
    val cardBorder: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

val LocalForesightExtendedColors = staticCompositionLocalOf {
    ForesightExtendedColors(
        riskSafe = RiskSafeDark,
        riskMedium = RiskMediumDark,
        riskHigh = RiskHighDark,
        riskCritical = RiskCriticalDark,
        surfaceElevated = DarkSurfaceElevated,
        cardBorder = DarkOutline,
        success = SuccessGreen,
        warning = WarningAmber,
        error = ErrorRed
    )
}

val DarkExtendedColors = ForesightExtendedColors(
    riskSafe = RiskSafeDark,
    riskMedium = RiskMediumDark,
    riskHigh = RiskHighDark,
    riskCritical = RiskCriticalDark,
    surfaceElevated = DarkSurfaceElevated,
    cardBorder = DarkOutline,
    success = SuccessGreen,
    warning = WarningAmber,
    error = ErrorRed
)

val LightExtendedColors = ForesightExtendedColors(
    riskSafe = RiskSafeLight,
    riskMedium = RiskMediumLight,
    riskHigh = RiskHighLight,
    riskCritical = RiskCriticalLight,
    surfaceElevated = LightSurfaceElevated,
    cardBorder = LightOutline,
    success = SuccessGreen,
    warning = Color(0xFFB07A00),
    error = Color(0xFFD64545)
)

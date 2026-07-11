package com.example.foresight.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

object Motion {
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1f)
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1f)

    const val DURATION_QUICK = 120
    const val DURATION_SHORT = 220
    const val DURATION_MEDIUM = 320
    const val DURATION_LONG = 450

    fun <T> quick() = tween<T>(DURATION_QUICK, easing = StandardEasing)
    fun <T> short() = tween<T>(DURATION_SHORT, easing = StandardEasing)
    fun <T> medium() = tween<T>(DURATION_MEDIUM, easing = EmphasizedEasing)
    fun <T> long() = tween<T>(DURATION_LONG, easing = EmphasizedEasing)
}

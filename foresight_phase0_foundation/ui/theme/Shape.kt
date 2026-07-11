package com.example.foresight.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Consistent corner-radius scale. Use these instead of ad-hoc RoundedCornerShape(Xdp)
 * scattered across screens, so every card/button/sheet rounds off the same way.
 */
val ForesightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, small badges
    small = RoundedCornerShape(12.dp),       // input fields, list rows
    medium = RoundedCornerShape(16.dp),      // buttons, standard cards
    large = RoundedCornerShape(24.dp),       // hero cards, sheets
    extraLarge = RoundedCornerShape(28.dp)   // bottom sheets, dialogs
)

// Named references for direct use where MaterialTheme.shapes isn't convenient
val ShapeChip = RoundedCornerShape(8.dp)
val ShapeRow = RoundedCornerShape(12.dp)
val ShapeCard = RoundedCornerShape(16.dp)
val ShapeHero = RoundedCornerShape(24.dp)
val ShapeSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

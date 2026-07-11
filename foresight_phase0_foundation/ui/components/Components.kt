package com.example.foresight.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import com.example.foresight.ui.theme.ShapeCard
import com.example.foresight.ui.theme.ShapeHero

/**
 * Standard elevated card used for all "content" surfaces (risk details, payment
 * summary, profile sections, etc). Replaces the copy-pasted Card+border pattern
 * that was duplicated with slightly different values in every screen.
 */
@Composable
fun FSCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ShapeCard,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, ExtendedTheme.colors.cardBorder)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            androidx.compose.foundation.layout.Column { content() }
        }
    }
}

/** Hero-sized variant with larger corner radius, for the primary summary card on a screen. */
@Composable
fun FSHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable () -> Unit
) = FSCard(modifier = modifier, shape = ShapeHero, contentPadding = contentPadding, content = content)

/**
 * Primary action button with a subtle press-scale animation (Material Motion style
 * feedback) instead of the flat default ripple-only press state.
 */
@Composable
fun FSPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.quick(),
        label = "buttonPressScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled && !loading,
        shape = ShapeCard,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f)
        ),
        interactionSource = interactionSource
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, fontSize = MaterialTheme.typography.labelLarge.fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

/** Secondary / low-emphasis action, for "Proceed anyway", "Cancel", etc. */
@Composable
fun FSSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = ShapeCard,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Small uppercase eyebrow-style header used above grouped content ("AI Analysis", "Business Rules"). */
@Composable
fun FSSectionHeader(title: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = title,
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
}

/** Circular icon badge used for the big status glyph on result/success/processing screens. */
@Composable
fun FSStatusBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/** Small pill-shaped badge for risk labels / status chips inline in lists. */
@Composable
fun RiskBadgeChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(com.example.foresight.ui.theme.ShapeChip)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** A label/value pair used for metrics (risk score, confidence, balance, etc). */
@Composable
fun MetricTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    androidx.compose.foundation.layout.Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

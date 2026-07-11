package com.example.foresight.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foresight.ui.components.FSCard
import com.example.foresight.ui.components.FSPrimaryButton
import com.example.foresight.ui.components.FSSecondaryButton
import com.example.foresight.ui.components.FSStatusBadge
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SuccessScreen(
    contactName: String,
    amount: String,
    onDoneClick: () -> Unit
) {
    val transactionId = remember { "FPAY${System.currentTimeMillis().toString().takeLast(6)}" }
    val date = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
    val time = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) }

    var startAnimation by remember { mutableStateOf(false) }
    val badgeScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = Motion.medium(),
        label = "badgeScale"
    )
    
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FSStatusBadge(
            icon = Icons.Default.Check,
            tint = ExtendedTheme.colors.riskSafe,
            modifier = Modifier.graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
            },
            size = 100.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = Motion.medium()) + slideInVertically(
                animationSpec = Motion.medium(),
                initialOffsetY = { it / 4 }
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Payment Successful",
                    color = ExtendedTheme.colors.riskSafe,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "₹ $amount paid to $contactName",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                FSCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailRow("Transaction ID", transactionId)
                        DetailRow("Date", date)
                        DetailRow("Time", time)
                        DetailRow("Status", "Completed", ExtendedTheme.colors.riskSafe)
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                FSPrimaryButton(
                    text = "Done",
                    onClick = onDoneClick
                )

                FSSecondaryButton(
                    text = "Back to Home",
                    onClick = onDoneClick
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

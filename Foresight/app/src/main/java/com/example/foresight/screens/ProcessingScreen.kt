package com.example.foresight.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.PaymentViewModel
import com.example.foresight.RiskState
import com.example.foresight.data.network.PredictionResponse
import com.example.foresight.ui.components.FSPrimaryButton
import com.example.foresight.ui.components.FSSecondaryButton
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun ProcessingScreen(
    viewModel: PaymentViewModel,
    contactName: String,
    contactPhone: String,
    amount: String,
    onProcessingFinished: (PredictionResponse) -> Unit,
    onBackClick: () -> Unit
) {
    val riskState by viewModel.riskState.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    val isTrusted = remember(contacts, contactPhone) {
        val found = contacts.find {
            it.phone.trim().filter { c -> c.isDigit() }.takeLast(10) ==
                    contactPhone.trim().filter { c -> c.isDigit() }.takeLast(10)
        }
        found?.isTrusted ?: false
    }

    val checklist = listOf(
        "Checking amount",
        "Checking beneficiary",
        "Checking trusted contacts",
        "Checking behavioral signals",
        "Running AI Risk Engine"
    )

    var visibleItems by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.analyzeRisk(amount, isTrusted, contactPhone)
        for (i in 1..checklist.size) {
            delay(400)
            visibleItems = i
        }
    }

    LaunchedEffect(riskState) {
        if (riskState is RiskState.Success) {
            kotlinx.coroutines.delay(500)
            onProcessingFinished((riskState as RiskState.Success).response)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (riskState is RiskState.Error) {
                ErrorDisplay(
                    message = (riskState as RiskState.Error).message,
                    onRetry = { viewModel.analyzeRisk(amount, isTrusted, contactPhone) },
                    onCancel = onBackClick
                )
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Analyzing transaction with AI...",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    checklist.forEachIndexed { index, item ->
                        AnimatedChecklistItem(text = item, isVisible = index < visibleItems)
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))

                Text(
                    "FORESIGHT AI PROTECTION",
                    color = ExtendedTheme.colors.riskSafe,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorDisplay(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = ExtendedTheme.colors.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Unable to connect to the AI Risk Engine.",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        FSPrimaryButton(
            text = "Retry",
            onClick = onRetry
        )
        Spacer(modifier = Modifier.height(12.dp))
        FSSecondaryButton(
            text = "Cancel",
            onClick = onCancel
        )
    }
}

@Composable
private fun AnimatedChecklistItem(text: String, isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = ExtendedTheme.colors.riskSafe,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (!isVisible) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

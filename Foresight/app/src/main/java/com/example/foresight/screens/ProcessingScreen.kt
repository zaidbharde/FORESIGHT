package com.example.foresight.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF070913)
private val AccentPurple = Color(0xFF7C4DFF)
private val AccentMint = Color(0xFF20E3B2)

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
        viewModel.analyzeRisk(amount, isTrusted)
        for (i in 1..checklist.size) {
            delay(400)
            visibleItems = i
        }
    }

    LaunchedEffect(riskState) {
        if (riskState is RiskState.Success) {
            // Give a small delay to finish the animation if it was too fast
            kotlinx.coroutines.delay(500)
            onProcessingFinished((riskState as RiskState.Success).response)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
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
                    onRetry = { viewModel.analyzeRisk(amount, isTrusted) },
                    onCancel = onBackClick
                )
            } else {
                CircularProgressIndicator(
                    color = AccentPurple,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Analyzing transaction with AI...",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
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
                    color = AccentMint,
                    fontSize = 12.sp,
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
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Unable to connect to the AI Risk Engine.",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
        }
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
                tint = AccentMint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
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
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

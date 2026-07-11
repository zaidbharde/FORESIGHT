package com.example.foresight.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foresight.UpiManager
import com.example.foresight.ui.components.*
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import com.example.foresight.ui.theme.ShapeCard
import com.example.foresight.ui.theme.ShapeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRDetailsScreen(
    upiUri: String,
    onBackClick: () -> Unit,
    onContinueClick: (String, String, String) -> Unit
) {
    val details = remember(upiUri) { UpiManager.parseUpiUri(upiUri) }
    
    if (details == null) {
        ErrorState(onBackClick)
        return
    }

    var amount by remember { mutableStateOf(details.am ?: "") }
    var note by remember { mutableStateOf(details.tn ?: "") }
    val isAmountEditable = details.am == null
    val canContinue = remember(amount) {
        val parsed = amount.toDoubleOrNull()
        parsed != null && parsed.isFinite() && parsed > 0.0
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Details", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { -it / 2 }
            ) {
                FSCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = details.pn ?: "Merchant / Recipient",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (!details.pn.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = ExtendedTheme.colors.riskSafe,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = details.pa,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 2 }
            ) {
                Column {
                    // Amount Field
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Amount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextField(
                            value = if (isAmountEditable) amount else "₹ ${details.am}",
                            onValueChange = {
                                if (isAmountEditable && it.all { char -> char.isDigit() || char == '.' } && it.count { char -> char == '.' } <= 1) {
                                    amount = it
                                }
                            },
                            readOnly = !isAmountEditable,
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShapeRow,
                            prefix = { if (isAmountEditable) Text("₹ ", style = MaterialTheme.typography.titleMedium) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Note Field
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Note (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("What's this for?", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShapeRow,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FSPrimaryButton(
                text = "Continue",
                onClick = { if (canContinue) onContinueClick(details.pn ?: "UPI Merchant", amount, note) },
                enabled = canContinue
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ExtendedTheme.colors.riskSafe,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI Security Enabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExtendedTheme.colors.riskSafe,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ErrorState(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FSStatusBadge(icon = Icons.Default.QrCode, tint = ExtendedTheme.colors.error)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Invalid UPI QR",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "The scanned QR code is not a valid UPI payment code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        FSPrimaryButton(text = "Go Back", onClick = onBackClick)
    }
}

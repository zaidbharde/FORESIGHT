package com.example.foresight.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
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
import androidx.compose.ui.unit.sp
import com.example.foresight.UpiManager

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentPurple = Color(0xFF7C4DFF)
private val TextSecondary = Color(0xFF9CA6BA)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Details", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(40.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = details.pn ?: "Merchant / Recipient",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = details.pa,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Amount Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Amount", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                TextField(
                    value = if (isAmountEditable) amount else "₹ ${details.am}",
                    onValueChange = {
                        if (isAmountEditable && it.all { char -> char.isDigit() || char == '.' } && it.count { char -> char == '.' } <= 1) {
                            amount = it
                        }
                    },
                    readOnly = !isAmountEditable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    prefix = { if (isAmountEditable) Text("₹ ", color = Color.White) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Note Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Note (Optional)", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("What's this for?", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { if (canContinue) onContinueClick(details.pn ?: "UPI Merchant", amount, note) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                enabled = canContinue
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorState(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Invalid UPI QR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("The scanned QR code is not a valid UPI payment code.", color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBackClick) { Text("Go Back") }
    }
}

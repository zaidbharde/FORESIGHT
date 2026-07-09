package com.example.foresight.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentMint = Color(0xFF20E3B2)
private val TextSecondary = Color(0xFF9CA6BA)

@Composable
fun SuccessScreen(
    contactName: String,
    amount: String,
    onDoneClick: () -> Unit
) {
    val transactionId = remember { "FPAY${System.currentTimeMillis().toString().takeLast(6)}" }
    val date = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
    val time = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AccentMint.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AccentMint,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Payment Successful",
            color = AccentMint,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "₹ $amount paid to $contactName",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow("Transaction ID", transactionId)
                DetailRow("Date", date)
                DetailRow("Time", time)
                DetailRow("Status", "Completed", AccentMint)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onDoneClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
        ) {
            Text("Done", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        TextButton(
            onClick = onDoneClick,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Back to Home", color = TextSecondary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

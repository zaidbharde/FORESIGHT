package com.example.foresight.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.foresight.PaymentViewModel
import com.example.foresight.UserViewModel

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentPurple = Color(0xFF7C4DFF)
private val TextSecondary = Color(0xFF9CA6BA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    userViewModel: UserViewModel,
    contactName: String,
    initial: String,
    colorValue: Int,
    onBackClick: () -> Unit,
    onContinueClick: (String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    
    val bankAccounts by userViewModel.bankAccounts.collectAsState()
    val primaryBank = bankAccounts.find { it.isPrimary }
    val balance = primaryBank?.balance ?: 0.0
    
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // Contact Info
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(colorValue).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color(colorValue),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Paying $contactName",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "+91 98765 43210",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // Amount Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "₹",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) {
                            amount = it
                            showError = false
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Start
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(IntrinsicSize.Min).defaultMinSize(minWidth = 40.dp)
                )
            }
            
            if (showError) {
                Text(
                    "Insufficient Balance in ${primaryBank?.bankName ?: "Bank"}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Add a note (optional)", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentPurple,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { 
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > balance) {
                        showError = true
                    } else if (amt > 0) {
                        onContinueClick(amount, note) 
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    disabledContainerColor = AccentPurple.copy(alpha = 0.5f)
                ),
                enabled = amount.isNotEmpty()
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentPurple)
    )
}

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.UserViewModel
import com.example.foresight.ui.components.FSCard
import com.example.foresight.ui.components.FSPrimaryButton
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import com.example.foresight.ui.theme.ShapeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    userViewModel: UserViewModel,
    contactName: String,
    contactPhone: String,
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
                title = { Text("Enter Amount", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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

            // Contact section
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = Motion.medium()) + slideInVertically(
                    animationSpec = Motion.medium(),
                    initialOffsetY = { -it / 4 }
                )
            ) {
                FSCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color(colorValue),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = contactName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = contactPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Amount Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = amount,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            amount = it
                            showError = false
                        }
                    },
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Start,
                        fontFamily = FontFamily.Monospace // Tabular numbers feel
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(IntrinsicSize.Min).defaultMinSize(minWidth = 40.dp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }

            if (showError) {
                Text(
                    "Insufficient Balance in ${primaryBank?.bankName ?: "Bank"}",
                    color = ExtendedTheme.colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    "Balance: ₹${String.format("%,.2f", balance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            FSPrimaryButton(
                text = "Proceed to Pay",
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > balance) {
                        showError = true
                    } else if (amt > 0) {
                        onContinueClick(amount, note)
                    }
                },
                enabled = amount.isNotEmpty() && amount.toDoubleOrNull() ?: 0.0 > 0
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

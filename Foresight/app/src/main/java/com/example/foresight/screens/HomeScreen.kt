package com.example.foresight.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.R
import com.example.foresight.*
import java.text.NumberFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: PaymentViewModel,
    userViewModel: UserViewModel,
    onScanQrClick: () -> Unit,
    onPayContactClick: () -> Unit,
    onBankTransferClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContactClick: (String, String, Int) -> Unit
) {
    val balance by userViewModel.bankAccounts.collectAsState()
    val primaryBank = balance.find { it.isPrimary } ?: BankAccount(
        bankName = "No Bank",
        accountHolderName = "",
        accountNumber = "",
        ifscCode = "",
        upiId = "none",
        isPrimary = true,
        balance = 0.0
    )

    val transactions by viewModel.transactions.collectAsState()
    val contactsState by viewModel.contacts.collectAsState()
    val user by userViewModel.userProfile.collectAsState()
    val strings = LocalAppStrings.current

    val displayContacts = contactsState.sortedWith(
        compareByDescending<ForesightContact> { it.lastInteraction }
            .thenBy { it.name }
    ).take(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        HomeHeader(user.name, onProfileClick, onNotificationsClick)
        BalanceCard(primaryBank)
        QuickActions(
            onScanQrClick = onScanQrClick,
            onPayContactClick = onPayContactClick,
            onBankTransferClick = onBankTransferClick,
            onHistoryClick = onHistoryClick
        )
        RecentContactsSection(
            contacts = displayContacts,
            onSeeAllClick = onPayContactClick,
            onContactClick = onContactClick
        )
        RecentTransactionsSection(
            transactions = transactions.take(3),
            onSeeAllClick = onHistoryClick
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HomeHeader(
    userName: String,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onProfileClick)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = strings.goodEvening + ",",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = userName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onNotificationsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun BalanceCard(bank: BankAccount) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val strings = LocalAppStrings.current
    val gradientStart = Color(0xFF35216F)
    val gradientEnd = Color(0xFF5A35D7)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(gradientStart, gradientEnd)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.totalBalance,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = bank.upiId,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = currencyFormatter.format(bank.balance).replace("INR", "₹").replace("Rs.", "₹"),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF20E3B2),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Foresight AI Shield Active",
                        color = Color(0xFF20E3B2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(
    onScanQrClick: () -> Unit,
    onPayContactClick: () -> Unit,
    onBankTransferClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionItem(strings.scanQr, Icons.Default.QrCode, onScanQrClick, Modifier.weight(1f))
        ActionItem(strings.payContact, Icons.Default.Contacts, onPayContactClick, Modifier.weight(1f))
        ActionItem(strings.bankTransfer, Icons.Default.AccountBalance, onBankTransferClick, Modifier.weight(1f))
        ActionItem(strings.history, Icons.Default.History, onHistoryClick, Modifier.weight(1f))
    }
}

@Composable
private fun ActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentContactsSection(
    contacts: List<ForesightContact>,
    onSeeAllClick: () -> Unit,
    onContactClick: (String, String, Int) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Contacts",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.seeAll,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onSeeAllClick)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            contacts.forEach { contact ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { 
                        onContactClick(contact.name, contact.phone, contact.color.toArgb()) 
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(contact.color.copy(alpha = 0.2f))
                            .border(1.dp, contact.color.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(1).uppercase(),
                            color = contact.color,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = contact.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<Transaction>,
    onSeeAllClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.recentTransactions,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.seeAll,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onSeeAllClick)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (transactions.isEmpty()) {
                    Text(
                        "No recent transactions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    transactions.forEachIndexed { index, transaction ->
                        TransactionItem(transaction)
                        if (index < transactions.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(transaction: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(transaction.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = transaction.initial,
                    color = transaction.color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = transaction.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val statusColor = when (transaction.status.lowercase()) {
                    "safe", "low" -> Color(0xFF20E3B2)
                    "medium", "warning" -> Color(0xFFFFC857)
                    else -> Color(0xFFFF6B6B)
                }
                Text(
                    text = "${transaction.date} • ${if (transaction.type == TransactionType.SENT) "Sent" else "Received"}",
                    color = statusColor,
                    fontSize = 12.sp
                )
            }
        }
        val amountColor = if (transaction.type == TransactionType.SENT) MaterialTheme.colorScheme.onSurface else Color(0xFF20E3B2)
        Text(
            text = "${if (transaction.type == TransactionType.SENT) "-" else "+"} ₹ ${transaction.amount}",
            color = amountColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

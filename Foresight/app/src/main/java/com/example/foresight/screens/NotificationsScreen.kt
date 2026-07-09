package com.example.foresight.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.foresight.NotificationItem
import com.example.foresight.NotificationType
import com.example.foresight.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentPurple = Color(0xFF7C4DFF)
private val AccentMint = Color(0xFF20E3B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val notifications by viewModel.notifications.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.markAllAsRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearNotifications() }) {
                            Text("Clear All", color = AccentPurple)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            EmptyNotificationsUI()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(notifications) { item ->
                    NotificationCard(item)
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem) {
    val icon = when (item.type) {
        NotificationType.PAYMENT -> Icons.Default.Payments
        NotificationType.SECURITY -> Icons.Default.Security
        NotificationType.BANK -> Icons.Default.AccountBalance
        NotificationType.PROMO -> Icons.Default.Celebration
    }
    
    val color = when (item.type) {
        NotificationType.PAYMENT -> AccentMint
        NotificationType.SECURITY -> Color(0xFFFFB84D)
        NotificationType.BANK -> AccentPurple
        NotificationType.PROMO -> Color(0xFFFF6D6D)
    }

    val dateString = remember(item.timestamp) {
        val timeFormat = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        timeFormat.format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentPurple)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dateString,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationsUI() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "All caught up!",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "No new notifications at the moment",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

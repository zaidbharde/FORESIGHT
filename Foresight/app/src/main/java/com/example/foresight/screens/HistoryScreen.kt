package com.example.foresight.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
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
import com.example.foresight.PaymentViewModel
import com.example.foresight.Transaction
import com.example.foresight.TransactionType
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: PaymentViewModel,
    onBackClick: () -> Unit
) {
    val allTransactions by viewModel.transactions.collectAsState()
    var selectedType by remember { mutableStateOf("All") }
    var selectedDateRange by remember { mutableStateOf("All Time") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val filteredTransactions = allTransactions.filter { transaction ->
        val matchesType = when (selectedType) {
            "Sent" -> transaction.type == TransactionType.SENT
            "Received" -> transaction.type == TransactionType.RECEIVED
            else -> true
        }
        
        val now = System.currentTimeMillis()
        val matchesDate = when (selectedDateRange) {
            "Today" -> transaction.timestamp > now - 86400000
            "This Week" -> transaction.timestamp > now - 604800000
            "This Month" -> transaction.timestamp > now - 2592000000L
            else -> true
        }
        
        val matchesSearch = transaction.name.contains(searchQuery, ignoreCase = true) ||
                transaction.amount.contains(searchQuery)
        
        matchesType && matchesDate && matchesSearch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search transactions...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true
                        )
                    } else {
                        Text("Transaction History", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isSearching = !isSearching 
                        if (!isSearching) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search, 
                            contentDescription = "Search", 
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Type Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                listOf("All", "Sent", "Received").forEach { type ->
                    FilterChip(
                        selected = selectedType == type, 
                        label = type,
                        onClick = { selectedType = type }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Date Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                listOf("All Time", "Today", "This Week", "This Month").forEach { range ->
                    Surface(
                        color = if (selectedDateRange == range) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        onClick = { selectedDateRange = range },
                        border = if (selectedDateRange == range) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = range,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selectedDateRange == range) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            if (filteredTransactions.isEmpty()) {
                EmptyState(searchQuery.isNotEmpty() || selectedType != "All" || selectedDateRange != "All Time")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val grouped = filteredTransactions.groupBy { it.date }
                    grouped.forEach { (date, items) ->
                        item {
                            Text(
                                text = date,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(items) { item ->
                            HistoryRow(item)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isFiltering: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (isFiltering) "No matches found" else "No transactions yet",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFiltering) "Try adjusting your search or filters" else "Your recent payments will appear here",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HistoryRow(item: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.initial, 
                    color = item.color, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = item.name, 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontSize = 15.sp, 
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${item.time} • ${item.bankName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 12.sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (item.type == TransactionType.SENT) "-" else "+"} ₹ ${item.amount}", 
                color = if (item.type == TransactionType.SENT) MaterialTheme.colorScheme.onSurface else Color(0xFF20E3B2), 
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            val statusColor = when (item.status.lowercase()) {
                "safe", "low" -> Color(0xFF20E3B2)
                "medium", "warning" -> Color(0xFFFFC857)
                else -> Color(0xFFFF6B6B)
            }
            Text(
                text = item.status,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

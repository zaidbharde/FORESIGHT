package com.example.foresight.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.R
import com.example.foresight.PaymentViewModel
import com.example.foresight.TransactionType
import com.example.foresight.UserViewModel
import com.example.foresight.ForesightRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: PaymentViewModel,
    userViewModel: UserViewModel,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onNavigateToSection: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val user by userViewModel.userProfile.collectAsState()
    
    val totalSent = remember(transactions) {
        transactions.filter { it.type == TransactionType.SENT }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalReceived = remember(transactions) {
        transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(user.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${user.upiId} | ${user.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))

            // Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem("Spent", "₹${totalSent.toInt()}", MaterialTheme.colorScheme.onSurface)
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    StatItem("Received", "₹${totalReceived.toInt()}", Color(0xFF20E3B2))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Sections
            ProfileSection("Payment Settings", listOf(
                ProfileItem("Personal Information", Icons.Default.Person, ForesightRoutes.PersonalInfo),
                ProfileItem("Bank Accounts", Icons.Default.AccountBalance, ForesightRoutes.BankAccounts),
                ProfileItem("Security", Icons.Default.Security, ForesightRoutes.Security)
            ), onNavigateToSection)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection("App Settings", listOf(
                ProfileItem("Notifications", Icons.Default.Notifications, ForesightRoutes.NotificationsSettings),
                ProfileItem("Language", Icons.Default.Language, ForesightRoutes.Language),
                ProfileItem("Theme", Icons.Default.Palette, ForesightRoutes.Theme)
            ), onNavigateToSection)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection("Support", listOf(
                ProfileItem("Help & Support", Icons.Default.Help, ForesightRoutes.Help),
                ProfileItem("About Foresight", Icons.Default.Info, ForesightRoutes.About)
            ), onNavigateToSection)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Text("Logout", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            Text("Version 1.0.0 (Production)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ProfileSection(title: String, items: List<ProfileItem>, onItemClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    ProfileRow(item, onItemClick)
                    if (index < items.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(item: ProfileItem, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.route) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private data class ProfileItem(val title: String, val icon: ImageVector, val route: String)

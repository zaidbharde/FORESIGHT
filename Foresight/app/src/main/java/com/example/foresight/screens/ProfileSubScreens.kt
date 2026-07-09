package com.example.foresight.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.AppSettings
import com.example.foresight.AppTheme
import com.example.foresight.AppLanguage
import com.example.foresight.BankAccount
import com.example.foresight.UserViewModel

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentPurple = Color(0xFF7C4DFF)
private val TextSecondary = Color(0xFF9CA6BA)
private val AccentMint = Color(0xFF20E3B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val accounts by viewModel.bankAccounts.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<BankAccount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank Accounts", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        floatingActionButton = {
            if (!showForm) {
                ExtendedFloatingActionButton(
                    onClick = { 
                        editingAccount = null
                        showForm = true 
                    },
                    containerColor = AccentPurple,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Bank Account") },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (accounts.isEmpty() && !showForm) {
                EmptyBankState { showForm = true }
            } else if (showForm) {
                BankAccountForm(
                    account = editingAccount,
                    onSave = { name, holder, num, ifsc, upi, balance ->
                        val accountToEdit = editingAccount
                        if (accountToEdit == null) {
                            viewModel.addBank(name, holder, num, ifsc, upi, balance)
                        } else {
                            viewModel.updateBank(accountToEdit.id, name, holder, num, ifsc, upi, balance)
                        }
                        showForm = false
                    },
                    onCancel = { showForm = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    accounts.forEach { account ->
                        BankCard(
                            account = account,
                            onSetPrimary = { viewModel.setPrimaryBank(account.id) },
                            onDelete = { viewModel.removeBank(account.id) },
                            onEdit = {
                                editingAccount = account
                                showForm = true
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun BankCard(
    account: BankAccount,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (account.isPrimary) AccentPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.bankName.take(1),
                            color = AccentPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = account.bankName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = account.accountHolderName,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Account Number", color = TextSecondary, fontSize = 12.sp)
                    Text(account.accountNumber, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Balance", color = TextSecondary, fontSize = 12.sp)
                    Text("₹ ${String.format("%.2f", account.balance)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("UPI ID", color = TextSecondary, fontSize = 12.sp)
                    Text(account.upiId, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                if (account.isPrimary) {
                    Surface(
                        color = AccentMint.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentMint, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Primary", color = AccentMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(onClick = onSetPrimary) {
                        Text("Set as Primary", color = AccentPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankAccountForm(
    account: BankAccount?,
    onSave: (String, String, String, String, String, Double) -> Unit,
    onCancel: () -> Unit
) {
    var bankName by remember { mutableStateOf(account?.bankName ?: "SBI") }
    var holderName by remember { mutableStateOf(account?.accountHolderName ?: "") }
    var accNumber by remember { mutableStateOf(account?.accountNumber ?: "") }
    var ifsc by remember { mutableStateOf(account?.ifscCode ?: "") }
    var upiId by remember { mutableStateOf(account?.upiId ?: "") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "5000") }
    
    var expanded by remember { mutableStateOf(false) }
    val banks = listOf("SBI", "HDFC Bank", "ICICI Bank", "Axis Bank", "Kotak Mahindra")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (account == null) "Add Bank Account" else "Edit Bank Account", 
            color = Color.White, 
            fontSize = 24.sp, 
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Link your bank account to start secure UPI payments", 
            color = TextSecondary, 
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Bank Name Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = bankName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Bank") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = formFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(CardBg)
            ) {
                banks.forEach { selection ->
                    DropdownMenuItem(
                        text = { Text(selection, color = Color.White) },
                        onClick = {
                            bankName = selection
                            expanded = false
                        }
                    )
                }
            }
        }

        FormField("Account Holder Name", holderName) { holderName = it }
        FormField("Account Number", accNumber, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { accNumber = it }
        FormField("IFSC Code", ifsc) { ifsc = it.uppercase() }
        FormField("UPI ID", upiId) { upiId = it.lowercase() }
        FormField("Opening Balance (₹)", balance, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) { balance = it }

        Spacer(modifier = Modifier.weight(1f))

        val isValid = holderName.isNotBlank() && 
                      accNumber.length >= 10 && 
                      ifsc.length == 11 && 
                      upiId.contains("@") && 
                      balance.toDoubleOrNull() != null

        Button(
            onClick = { onSave(bankName, holderName, accNumber, ifsc, upiId, balance.toDoubleOrNull() ?: 0.0) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
            enabled = isValid
        ) {
            Text(if (account == null) "Link Account" else "Update Account", fontWeight = FontWeight.Bold)
        }
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = formFieldColors(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun formFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = CardBg,
    unfocusedContainerColor = CardBg,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = AccentPurple,
    unfocusedLabelColor = TextSecondary
)

@Composable
private fun EmptyBankState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("No Bank Account Linked", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You need at least one bank account to send or receive payments via UPI.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Add Bank Account", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val user by viewModel.userProfile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Information", color = Color.White, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            InfoItem("Full Name", user.name)
            InfoItem("Mobile Number", user.phone)
            InfoItem("UPI ID", user.upiId)
            InfoItem("Joined Date", user.joinedDate)
            InfoItem("KYC Status", "Verified", AccentMint)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, valueColor: Color = Color.White) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security", color = Color.White, fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("Authentication", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            SecurityToggle("Fingerprint Login", settings.isBiometricEnabled) {
                viewModel.updateSettings(settings.copy(isBiometricEnabled = it))
            }
            SecurityToggle("Face Unlock", settings.isFaceUnlockEnabled) {
                viewModel.updateSettings(settings.copy(isFaceUnlockEnabled = it))
            }
            SecurityToggle("App Lock", settings.isAppLockEnabled) {
                viewModel.updateSettings(settings.copy(isAppLockEnabled = it))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("App Protection", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            SecurityToggle("Auto Lock on Minimize", settings.isAutoLockEnabled) {
                viewModel.updateSettings(settings.copy(isAutoLockEnabled = it))
            }
            SecurityToggle("AI Fraud Shield", settings.isAiProtectionEnabled) {
                viewModel.updateSettings(settings.copy(isAiProtectionEnabled = it))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Trusted Devices", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            TrustedDeviceItem("Samsung Galaxy S24 Ultra", "Current Device")
            TrustedDeviceItem("iPad Pro 11\"", "Last active: 2 hours ago")
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { /* Dummy */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg)
            ) {
                Text("Change Transaction PIN", color = Color.White)
            }
        }
    }
}

@Composable
private fun SecurityToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentPurple, checkedTrackColor = AccentPurple.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun TrustedDeviceItem(name: String, status: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Smartphone, contentDescription = null, tint = TextSecondary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(status, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = Color.White, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            SettingToggle("Push Notifications", settings.isNotificationsEnabled) {
                viewModel.updateSettings(settings.copy(isNotificationsEnabled = it))
            }
            SettingToggle("Payment Alerts", settings.isPaymentSoundsEnabled) {
                viewModel.updateSettings(settings.copy(isPaymentSoundsEnabled = it))
            }
            SettingToggle("Security Alerts", true) {}
            SettingToggle("Marketing Updates", false) {}
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentPurple, checkedTrackColor = AccentPurple.copy(alpha = 0.4f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val languages = listOf(
        AppLanguage.ENGLISH to "English",
        AppLanguage.HINDI to "Hindi"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Language", color = Color.White, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            languages.forEach { (lang, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setLanguage(lang) }.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp)
                    RadioButton(
                        selected = settings.language == lang, 
                        onClick = { viewModel.setLanguage(lang) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentPurple)
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme", color = Color.White, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            SettingToggle("Dark Mode", settings.theme == AppTheme.DARK) {
                if (it) viewModel.setTheme(AppTheme.DARK) else viewModel.setTheme(AppTheme.LIGHT)
            }
            SettingToggle("System Default", settings.theme == AppTheme.SYSTEM) {
                if (it) viewModel.setTheme(AppTheme.SYSTEM) else viewModel.setTheme(AppTheme.LIGHT)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", color = Color.White, fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(AccentPurple),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("FORESIGHT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Version 1.0.0", color = TextSecondary, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                "Foresight is an AI-powered fintech application designed to provide the highest level of security for your digital payments.",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            AboutRow("Terms of Service")
            AboutRow("Privacy Policy")
            AboutRow("Open Source Licenses")
            
            Spacer(modifier = Modifier.weight(1f))
            Text("© 2024 Foresight Inc.", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AboutRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {}.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", color = Color.White, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            SupportItem("Help Center", Icons.Default.QuestionAnswer)
            SupportItem("Contact Us", Icons.Default.Email)
            SupportItem("Raise a Dispute", Icons.Default.ReportProblem)
            SupportItem("FAQs", Icons.Default.Info)
        }
    }
}

@Composable
private fun SupportItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentPurple)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

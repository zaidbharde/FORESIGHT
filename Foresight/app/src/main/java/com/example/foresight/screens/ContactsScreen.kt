package com.example.foresight.screens

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foresight.ForesightContact
import com.example.foresight.PaymentViewModel
import com.example.foresight.ui.components.FSCard
import com.example.foresight.ui.components.FSPrimaryButton
import com.example.foresight.ui.theme.ExtendedTheme
import com.example.foresight.ui.theme.Motion
import com.example.foresight.ui.theme.ShapeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: PaymentViewModel,
    onBackClick: () -> Unit,
    onContactClick: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allContacts by viewModel.contacts.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> permissionGranted = granted }
    )

    LaunchedEffect(permissionGranted) {
        if (permissionGranted && (allContacts.isEmpty() || allContacts.any { it.isDemo })) {
            isLoading = true
            val fetched = fetchContacts(context)
            viewModel.setContacts(fetched)
            isLoading = false
        }
    }

    val filteredContacts = remember(searchQuery, allContacts) {
        val filtered = if (searchQuery.isEmpty()) {
            allContacts
        } else {
            allContacts.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) 
            }
        }
        filtered.sortedWith(
            compareByDescending<ForesightContact> { it.isTrusted }
                .thenByDescending { it.lastInteraction }
                .thenBy { it.name }
        )
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Contact", style = MaterialTheme.typography.titleMedium) },
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!permissionGranted) {
                PermissionRequestUI { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else if (allContacts.isEmpty() && !isLoading) {
                EmptyContactsUI()
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        placeholder = { Text("Search by name or number", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                        singleLine = true
                    )

                    AnimatedVisibility(
                        visible = startAnimation,
                        enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 4 }
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val trusted = filteredContacts.filter { it.isTrusted }
                            val others = filteredContacts.filter { !it.isTrusted }

                            if (trusted.isNotEmpty() && searchQuery.isEmpty()) {
                                item {
                                    Text(
                                        text = "Trusted Contacts",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ExtendedTheme.colors.riskSafe,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(trusted) { contact ->
                                    ContactCard(
                                        contact = contact,
                                        onContactClick = { 
                                            onContactClick(contact.name, contact.name.take(1).uppercase(), contact.color.toArgb()) 
                                        },
                                        onToggleTrusted = { viewModel.toggleTrusted(contact.id) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }

                            if (others.isNotEmpty()) {
                                item {
                                    Text(
                                        text = if (trusted.isNotEmpty() && searchQuery.isEmpty()) "Other Contacts" else "Contacts",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(others) { contact ->
                                    ContactCard(
                                        contact = contact,
                                        onContactClick = { 
                                            onContactClick(contact.name, contact.name.take(1).uppercase(), contact.color.toArgb()) 
                                        },
                                        onToggleTrusted = { viewModel.toggleTrusted(contact.id) }
                                    )
                                }
                            } else if (filteredContacts.isEmpty()) {
                                item {
                                    Text(
                                        "No contacts found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestUI(onAllowClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Access Contacts",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Foresight needs contact access to help you make payments to your friends and family securely.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
        Spacer(modifier = Modifier.height(40.dp))
        FSPrimaryButton(
            text = "Allow Contacts",
            onClick = onAllowClick
        )
    }
}

@Composable
private fun EmptyContactsUI() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No contacts found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Please check your contact permissions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactCard(
    contact: ForesightContact, 
    onContactClick: () -> Unit,
    onToggleTrusted: () -> Unit
) {
    FSCard(
        modifier = Modifier.clickable(onClick = onContactClick),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(contact.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    color = contact.color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (contact.isTrusted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Trusted",
                            tint = ExtendedTheme.colors.riskSafe,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = contact.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contact.upiId.isNotBlank()) {
                    Text(
                        text = contact.upiId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onToggleTrusted) {
                Icon(
                    imageVector = if (contact.isTrusted) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle Trusted",
                    tint = if (contact.isTrusted) ExtendedTheme.colors.riskSafe else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private suspend fun fetchContacts(context: Context): List<ForesightContact> = withContext(Dispatchers.IO) {
    val contactMap = mutableMapOf<String, ForesightContact>() // Key: Comparison Number
    val contentResolver: ContentResolver = context.contentResolver
    
    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        
        val colors = listOf(
            Color(0xFFFF6D6D), Color(0xFF7C4DFF), Color(0xFF6D8DFF), 
            Color(0xFFFF5C8A), Color(0xFF20E3B2), Color(0xFFFFB84D)
        )

        while (it.moveToNext()) {
            val rawName = it.getString(nameIndex) ?: ""
            val number = it.getString(numberIndex) ?: continue
            
            // Normalize for comparison: Strip non-digits and take last 10
            val comparisonNumber = number.filter { char -> char.isDigit() }.takeLast(10)
            if (comparisonNumber.isEmpty()) continue

            // If we already have this number, we skip. 
            // We prioritize entries with names since query is sorted by name.
            if (!contactMap.containsKey(comparisonNumber)) {
                val name = if (rawName.isBlank()) number else rawName
                contactMap[comparisonNumber] = ForesightContact(
                    name = name, 
                    phone = number, 
                    color = colors.random(),
                    isDemo = false
                )
            } else {
                // If existing entry has a numeric name (likely just the phone number) 
                // and this new one has a real name, update it.
                val existing = contactMap[comparisonNumber] ?: continue
                val existingIsNumeric = existing.name.all { c -> c.isDigit() || c == '+' || c == ' ' }
                val newIsNumeric = rawName.isBlank() || rawName.all { c -> c.isDigit() || c == '+' || c == ' ' }
                
                if (existingIsNumeric && !newIsNumeric) {
                    contactMap[comparisonNumber] = existing.copy(name = rawName)
                }
            }
        }
    }
    contactMap.values.sortedBy { it.name }.toList()
}



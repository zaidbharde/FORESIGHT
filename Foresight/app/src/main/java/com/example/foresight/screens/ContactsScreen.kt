package com.example.foresight.screens

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.foresight.ForesightContact
import com.example.foresight.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val TextSecondary = Color(0xFF9CA6BA)
private val AccentPurple = Color(0xFF7C4DFF)

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
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Contact", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!permissionGranted) {
                PermissionRequestUI { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentPurple)
            } else if (allContacts.isEmpty() && !isLoading) {
                EmptyContactsUI()
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        placeholder = { Text("Search by name or number", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = AccentPurple,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val trusted = filteredContacts.filter { it.isTrusted }
                        val others = filteredContacts.filter { !it.isTrusted }

                        if (trusted.isNotEmpty() && searchQuery.isEmpty()) {
                            item {
                                Text(
                                    "Trusted Contacts",
                                    color = Color(0xFF20E3B2),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(trusted) { contact ->
                                ContactRow(
                                    contact = contact,
                                    onContactClick = { onContactClick(contact.name, contact.name.take(1).uppercase(), contact.color.toArgb()) },
                                    onToggleTrusted = { viewModel.toggleTrusted(contact.id) }
                                )
                            }
                            item { HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
                        }

                        if (others.isNotEmpty()) {
                            item {
                                Text(
                                    if (trusted.isNotEmpty() && searchQuery.isEmpty()) "Other Contacts" else "Contacts",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(others) { contact ->
                                ContactRow(
                                    contact = contact,
                                    onContactClick = { onContactClick(contact.name, contact.name.take(1).uppercase(), contact.color.toArgb()) },
                                    onToggleTrusted = { viewModel.toggleTrusted(contact.id) }
                                )
                            }
                        } else if (filteredContacts.isEmpty()) {
                            item {
                                Text(
                                    "No contacts found",
                                    color = TextSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        item { Spacer(modifier = Modifier.height(20.dp)) }
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
            modifier = Modifier.size(80.dp).clip(CircleShape).background(AccentPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Contacts, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Access Contacts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Foresight needs contact access to help you make payments to your friends and family securely.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onAllowClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Allow Contacts", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyContactsUI() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No contacts found", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Please check your contact permissions", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ContactRow(
    contact: ForesightContact, 
    onContactClick: () -> Unit,
    onToggleTrusted: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onContactClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(contact.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                color = contact.color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (contact.isTrusted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Star, contentDescription = "Trusted", tint = Color(0xFF20E3B2), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = contact.phone,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onToggleTrusted) {
            Icon(
                imageVector = if (contact.isTrusted) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Toggle Trusted",
                tint = if (contact.isTrusted) Color(0xFF20E3B2) else TextSecondary.copy(alpha = 0.5f)
            )
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

private fun Color.toArgb(): Int {
    return (this.alpha * 255.0f + 0.5f).toInt() shl 24 or
            ((this.red * 255.0f + 0.5f).toInt() shl 16) or
            ((this.green * 255.0f + 0.5f).toInt() shl 8) or
            (this.blue * 255.0f + 0.5f).toInt()
}

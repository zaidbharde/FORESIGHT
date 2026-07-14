package com.example.foresight

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.example.foresight.data.auth.FirebaseAuthManager
import com.example.foresight.screens.*

object ForesightRoutes {
    const val Splash = "splash"
    const val Welcome = "welcome"
    const val Login = "login"
    
    const val Home = "home"
    const val Scan = "scan"
    const val History = "history"
    const val Profile = "profile"
    
    // Profile Sub-screens
    const val PersonalInfo = "personal_info"
    const val BankAccounts = "bank_accounts"
    const val Security = "security"
    const val NotificationsSettings = "notifications_settings"
    const val Language = "language"
    const val Theme = "theme"
    const val About = "about"
    const val Help = "help"

    const val Contacts = "contacts"
    const val Payment = "payment/{contactName}/{initial}/{color}"
    const val ReviewPayment = "review_payment/{contactName}/{amount}/{note}/{color}/{payeeVpa}?mc={mc}&tr={tr}&tid={tid}&mode={mode}&orgid={orgid}&sign={sign}&url={url}&purpose={purpose}&mam={mam}"
    const val Processing = "processing/{contactName}/{phone}/{amount}/{payeeVpa}?mc={mc}&tr={tr}&tid={tid}&mode={mode}&orgid={orgid}&sign={sign}&url={url}&purpose={purpose}&mam={mam}"
    const val RiskResult = "risk_result/{status}/{contactName}/{amount}/{note}/{color}/{payeeVpa}?mc={mc}&tr={tr}&tid={tid}&mode={mode}&orgid={orgid}&sign={sign}&url={url}&purpose={purpose}&mam={mam}"

    const val Success = "success/{contactName}/{amount}"
    const val QRDetails = "qr_details/{upiUri}"
    const val Notifications = "notifications"

    fun payment(name: String, initial: String, color: Int) = "payment/$name/$initial/$color"
    
    fun review(
        name: String, 
        amount: String, 
        note: String, 
        color: Int, 
        vpa: String = "", 
        mc: String? = null, 
        tr: String? = null, 
        tid: String? = null,
        mode: String? = null, 
        orgid: String? = null, 
        sign: String? = null,
        url: String? = null,
        purpose: String? = null,
        mam: String? = null
    ): String {
        val base = "review_payment/$name/$amount/${if (note.isEmpty()) "Payment" else note}/$color/${if (vpa.isEmpty()) "none" else vpa}"
        val query = mutableListOf<String>()
        mc?.let { query.add("mc=$it") }
        tr?.let { query.add("tr=$it") }
        tid?.let { query.add("tid=$it") }
        mode?.let { query.add("mode=$it") }
        orgid?.let { query.add("orgid=$it") }
        sign?.let { query.add("sign=$it") }
        url?.let { query.add("url=$it") }
        purpose?.let { query.add("purpose=$it") }
        mam?.let { query.add("mam=$it") }
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }

    fun processing(
        name: String, 
        phone: String, 
        amount: String, 
        vpa: String = "", 
        mc: String? = null, 
        tr: String? = null, 
        tid: String? = null,
        mode: String? = null, 
        orgid: String? = null, 
        sign: String? = null,
        url: String? = null,
        purpose: String? = null,
        mam: String? = null
    ): String {
        val base = "processing/$name/$phone/$amount/${if (vpa.isEmpty()) "none" else vpa}"
        val query = mutableListOf<String>()
        mc?.let { query.add("mc=$it") }
        tr?.let { query.add("tr=$it") }
        tid?.let { query.add("tid=$it") }
        mode?.let { query.add("mode=$it") }
        orgid?.let { query.add("orgid=$it") }
        sign?.let { query.add("sign=$it") }
        url?.let { query.add("url=$it") }
        purpose?.let { query.add("purpose=$it") }
        mam?.let { query.add("mam=$it") }
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }

    fun riskResult(
        status: String, 
        name: String, 
        amount: String, 
        note: String, 
        color: Int, 
        vpa: String = "", 
        mc: String? = null, 
        tr: String? = null, 
        tid: String? = null,
        mode: String? = null, 
        orgid: String? = null, 
        sign: String? = null,
        url: String? = null,
        purpose: String? = null,
        mam: String? = null
    ): String {
        val base = "risk_result/$status/$name/$amount/${if (note.isEmpty()) "Payment" else note}/$color/${if (vpa.isEmpty()) "none" else vpa}"
        val query = mutableListOf<String>()
        mc?.let { query.add("mc=$it") }
        tr?.let { query.add("tr=$it") }
        tid?.let { query.add("tid=$it") }
        mode?.let { query.add("mode=$it") }
        orgid?.let { query.add("orgid=$it") }
        sign?.let { query.add("sign=$it") }
        url?.let { query.add("url=$it") }
        purpose?.let { query.add("purpose=$it") }
        mam?.let { query.add("mam=$it") }
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }
    fun success(name: String, amount: String) = "success/$name/$amount"
    fun qrDetails(uri: String) = "qr_details/${android.net.Uri.encode(uri)}"
}

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val viewModel: PaymentViewModel = viewModel()
    val userViewModel: UserViewModel = viewModel()
    
    val isLoggedIn by userViewModel.isLoggedIn.collectAsState()
    val publicRoutes = rememberPublicRoutes()

    LaunchedEffect(isLoggedIn, currentRoute) {
        if (!isLoggedIn && currentRoute != null && currentRoute !in publicRoutes) {
            navController.navigate(ForesightRoutes.Welcome) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf(ForesightRoutes.Home, ForesightRoutes.Scan, ForesightRoutes.History, ForesightRoutes.Profile)) {
                BottomNavigationBar(
                    currentRoute = currentRoute ?: ForesightRoutes.Home,
                    onNavItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ForesightRoutes.Splash,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ForesightRoutes.Splash) {
                SplashScreen(onSplashFinished = {
                    val target = if (FirebaseAuthManager().isLoggedIn()) ForesightRoutes.Home else ForesightRoutes.Welcome
                    navController.navigate(target) {
                        popUpTo(ForesightRoutes.Splash) { inclusive = true }
                    }
                })
            }

            composable(ForesightRoutes.Welcome) {
                WelcomeScreen(onGetStartedClick = {
                    navController.navigate(ForesightRoutes.Login)
                })
            }

            composable(ForesightRoutes.Login) {
                LoginScreen(onLoginSuccess = { phone ->
                    userViewModel.login(phone)
                    navController.navigate(ForesightRoutes.Home) {
                        popUpTo(ForesightRoutes.Welcome) { inclusive = true }
                    }
                }, onBackClick = { navController.popBackStack() })
            }

            composable(ForesightRoutes.Home) {
                HomeScreen(
                    viewModel = viewModel,
                    userViewModel = userViewModel,
                    onScanQrClick = { navController.navigate(ForesightRoutes.Scan) },
                    onPayContactClick = { navController.navigate(ForesightRoutes.Contacts) },
                    onBankTransferClick = { navController.navigate(ForesightRoutes.Contacts) },
                    onHistoryClick = { navController.navigate(ForesightRoutes.History) },
                    onProfileClick = { navController.navigate(ForesightRoutes.Profile) },
                    onNotificationsClick = { navController.navigate(ForesightRoutes.Notifications) },
                    onContactClick = { name, initial, color -> 
                        navController.navigate(ForesightRoutes.payment(name, initial, color)) 
                    }
                )
            }

            composable(ForesightRoutes.Scan) {
                QRScannerScreen(
                    onBackClick = { navController.popBackStack() },
                    onQrScanned = { uri ->
                        navController.navigate(ForesightRoutes.qrDetails(uri)) {
                            popUpTo(ForesightRoutes.Scan) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = ForesightRoutes.QRDetails,
                arguments = listOf(navArgument("upiUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("upiUri") ?: ""
                val upiDetails = UpiManager.parseUpiUri(uri)
                QRDetailsScreen(
                    upiUri = uri,
                    onBackClick = { navController.popBackStack() },
                    onContinueClick = { name, amount, note ->
                        navController.navigate(
                            ForesightRoutes.review(
                                name, amount, note, 0xFF7C4DFF.toInt(), 
                                upiDetails?.pa ?: "",
                                mc = upiDetails?.mc,
                                tr = upiDetails?.tr,
                                tid = upiDetails?.tid,
                                mode = upiDetails?.mode,
                                orgid = upiDetails?.orgid,
                                sign = upiDetails?.sign,
                                url = upiDetails?.url,
                                purpose = upiDetails?.purpose,
                                mam = upiDetails?.mam
                            )
                        )
                    }
                )
            }

            composable(ForesightRoutes.History) {
                HistoryScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(ForesightRoutes.Profile) {
                ProfileScreen(
                    viewModel = viewModel,
                    userViewModel = userViewModel,
                    onBackClick = { navController.popBackStack() },
                    onLogoutClick = {
                        userViewModel.logout()
                        navController.navigate(ForesightRoutes.Welcome) {
                            popUpTo(ForesightRoutes.Home) { inclusive = true }
                        }
                    },
                    onNavigateToSection = { route ->
                        navController.navigate(route)
                    }
                )
            }

            // Profile Sub-screens
            composable(ForesightRoutes.PersonalInfo) { PersonalInfoScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.BankAccounts) { BankAccountsScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.Security) { SecurityScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.NotificationsSettings) { NotificationsSettingsScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.Language) { LanguageScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.Theme) { ThemeSettingsScreen(userViewModel) { navController.popBackStack() } }
            composable(ForesightRoutes.About) { AboutScreen { navController.popBackStack() } }
            composable(ForesightRoutes.Help) { HelpSupportScreen { navController.popBackStack() } }

            composable(ForesightRoutes.Contacts) {
                ContactsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onContactClick = { name, initial, color ->
                        navController.navigate(ForesightRoutes.payment(name, initial, color))
                    }
                )
            }

            composable(
                route = ForesightRoutes.Payment,
                arguments = listOf(
                    navArgument("contactName") { type = NavType.StringType },
                    navArgument("initial") { type = NavType.StringType },
                    navArgument("color") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("contactName") ?: ""
                val initial = backStackEntry.arguments?.getString("initial") ?: ""
                val colorValue = backStackEntry.arguments?.getInt("color") ?: 0xFF7C4DFF.toInt()
                val contacts by viewModel.contacts.collectAsState()
                val contact = contacts.find { it.name == name }
                val phone = contact?.phone ?: ""
                PaymentScreen(
                    userViewModel = userViewModel,
                    contactName = name,
                    contactPhone = phone,
                    initial = initial,
                    colorValue = colorValue,
                    onBackClick = { navController.popBackStack() },
                    onContinueClick = { amount, note ->
                        navController.navigate(ForesightRoutes.review(name, amount, note, colorValue))
                    }
                )
            }

            composable(
                route = ForesightRoutes.ReviewPayment,
                arguments = listOf(
                    navArgument("contactName") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.StringType },
                    navArgument("note") { type = NavType.StringType },
                    navArgument("color") { type = NavType.IntType },
                    navArgument("payeeVpa") { type = NavType.StringType },
                    navArgument("mc") { type = NavType.StringType; nullable = true },
                    navArgument("tr") { type = NavType.StringType; nullable = true },
                    navArgument("tid") { type = NavType.StringType; nullable = true },
                    navArgument("mode") { type = NavType.StringType; nullable = true },
                    navArgument("orgid") { type = NavType.StringType; nullable = true },
                    navArgument("sign") { type = NavType.StringType; nullable = true },
                    navArgument("url") { type = NavType.StringType; nullable = true },
                    navArgument("purpose") { type = NavType.StringType; nullable = true },
                    navArgument("mam") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("contactName") ?: ""
                val amount = backStackEntry.arguments?.getString("amount") ?: ""
                val note = backStackEntry.arguments?.getString("note") ?: ""
                val color = backStackEntry.arguments?.getInt("color") ?: 0
                val vpa = backStackEntry.arguments?.getString("payeeVpa") ?: ""
                
                val mc = backStackEntry.arguments?.getString("mc")
                val tr = backStackEntry.arguments?.getString("tr")
                val tid = backStackEntry.arguments?.getString("tid")
                val mode = backStackEntry.arguments?.getString("mode")
                val orgid = backStackEntry.arguments?.getString("orgid")
                val sign = backStackEntry.arguments?.getString("sign")
                val url = backStackEntry.arguments?.getString("url")
                val purpose = backStackEntry.arguments?.getString("purpose")
                val mam = backStackEntry.arguments?.getString("mam")
                
                val bankAccounts by userViewModel.bankAccounts.collectAsState()
                val primaryBank = bankAccounts.find { it.isPrimary } ?: BankAccount(
                    bankName = "No Bank",
                    accountHolderName = "",
                    accountNumber = "",
                    ifscCode = "",
                    upiId = "none",
                    isPrimary = true,
                    balance = 0.0
                )

                // Get phone number from contacts for processing
                val contacts by viewModel.contacts.collectAsState()
                val contact = contacts.find { it.name == name }
                val phone = contact?.phone ?: ""
                val finalVpa = if (vpa == "none" || vpa.isEmpty()) contact?.upiId ?: "" else vpa
                
                ReviewPaymentScreen(
                    contactName = name,
                    contactPhone = phone,
                    amount = amount,
                    note = note,
                    primaryBank = primaryBank,
                    onBackClick = { navController.popBackStack() },
                    onPayClick = {
                        navController.navigate(ForesightRoutes.processing(name, phone, amount, finalVpa, mc, tr, tid, mode, orgid, sign, url, purpose, mam))
                    }
                )
            }

            composable(
                route = ForesightRoutes.Processing,
                arguments = listOf(
                    navArgument("contactName") { type = NavType.StringType },
                    navArgument("phone") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.StringType },
                    navArgument("payeeVpa") { type = NavType.StringType },
                    navArgument("mc") { type = NavType.StringType; nullable = true },
                    navArgument("tr") { type = NavType.StringType; nullable = true },
                    navArgument("tid") { type = NavType.StringType; nullable = true },
                    navArgument("mode") { type = NavType.StringType; nullable = true },
                    navArgument("orgid") { type = NavType.StringType; nullable = true },
                    navArgument("sign") { type = NavType.StringType; nullable = true },
                    navArgument("url") { type = NavType.StringType; nullable = true },
                    navArgument("purpose") { type = NavType.StringType; nullable = true },
                    navArgument("mam") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("contactName") ?: ""
                val phone = backStackEntry.arguments?.getString("phone") ?: ""
                val amount = backStackEntry.arguments?.getString("amount") ?: ""
                val vpa = backStackEntry.arguments?.getString("payeeVpa") ?: ""
                
                val mc = backStackEntry.arguments?.getString("mc")
                val tr = backStackEntry.arguments?.getString("tr")
                val tid = backStackEntry.arguments?.getString("tid")
                val mode = backStackEntry.arguments?.getString("mode")
                val orgid = backStackEntry.arguments?.getString("orgid")
                val sign = backStackEntry.arguments?.getString("sign")
                val url = backStackEntry.arguments?.getString("url")
                val purpose = backStackEntry.arguments?.getString("purpose")
                val mam = backStackEntry.arguments?.getString("mam")

                ProcessingScreen(
                    viewModel = viewModel,
                    contactName = name,
                    contactPhone = phone,
                    amount = amount,
                    onProcessingFinished = { _ ->
                        navController.navigate(ForesightRoutes.riskResult("analyzed", name, amount, "Payment", 0xFF7C4DFF.toInt(), vpa, mc, tr, tid, mode, orgid, sign, url, purpose, mam)) {
                            popUpTo(ForesightRoutes.Home)
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = ForesightRoutes.RiskResult,
                arguments = listOf(
                    navArgument("status") { type = NavType.StringType },
                    navArgument("contactName") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.StringType },
                    navArgument("note") { type = NavType.StringType },
                    navArgument("color") { type = NavType.IntType },
                    navArgument("payeeVpa") { type = NavType.StringType },
                    navArgument("mc") { type = NavType.StringType; nullable = true },
                    navArgument("tr") { type = NavType.StringType; nullable = true },
                    navArgument("tid") { type = NavType.StringType; nullable = true },
                    navArgument("mode") { type = NavType.StringType; nullable = true },
                    navArgument("orgid") { type = NavType.StringType; nullable = true },
                    navArgument("sign") { type = NavType.StringType; nullable = true },
                    navArgument("url") { type = NavType.StringType; nullable = true },
                    navArgument("purpose") { type = NavType.StringType; nullable = true },
                    navArgument("mam") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("contactName") ?: ""
                val amount = backStackEntry.arguments?.getString("amount") ?: ""
                val note = backStackEntry.arguments?.getString("note") ?: ""
                val colorValue = backStackEntry.arguments?.getInt("color") ?: 0
                val vpa = backStackEntry.arguments?.getString("payeeVpa") ?: ""
                
                val mc = backStackEntry.arguments?.getString("mc")
                val tr = backStackEntry.arguments?.getString("tr")
                val tid = backStackEntry.arguments?.getString("tid")
                val mode = backStackEntry.arguments?.getString("mode")
                val orgid = backStackEntry.arguments?.getString("orgid")
                val sign = backStackEntry.arguments?.getString("sign")
                val url = backStackEntry.arguments?.getString("url")
                val purpose = backStackEntry.arguments?.getString("purpose")
                val mam = backStackEntry.arguments?.getString("mam")
                
                val prediction by viewModel.currentPrediction.collectAsState()
                
                val bankAccounts by userViewModel.bankAccounts.collectAsState()
                val primaryBank = bankAccounts.find { it.isPrimary } ?: BankAccount(
                    bankName = "No Bank",
                    accountHolderName = "",
                    accountNumber = "",
                    ifscCode = "",
                    upiId = "none",
                    isPrimary = true,
                    balance = 0.0
                )

                prediction?.let { currentPrediction ->
                    RiskResultScreen(
                        prediction = currentPrediction,
                        contactName = name,
                        amount = amount,
                        onPaymentSuccess = {
                            userViewModel.deductFromPrimary(amount.toDoubleOrNull() ?: 0.0)
                            viewModel.performPayment(
                                name,
                                name.take(1),
                                amount,
                                note,
                                Color(colorValue),
                                primaryBank.bankName,
                                currentPrediction
                            )
                            navController.navigate(ForesightRoutes.success(name, amount)) {
                                popUpTo(ForesightRoutes.Home)
                            }
                        },
                        onCancelClick = {
                            viewModel.resetRiskState()
                            navController.popBackStack(ForesightRoutes.Home, inclusive = false)
                        },
                        payeeVpa = vpa,
                        transactionNote = note,
                        mc = mc,
                        tr = tr,
                        tid = tid,
                        mode = mode,
                        orgid = orgid,
                        sign = sign,
                        url = url,
                        purpose = purpose,
                        mam = mam
                    )
                }
            }

            composable(
                route = ForesightRoutes.Success,
                arguments = listOf(
                    navArgument("contactName") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("contactName") ?: ""
                val amount = backStackEntry.arguments?.getString("amount") ?: ""
                SuccessScreen(
                    contactName = name,
                    amount = amount,
                    onDoneClick = {
                        navController.popBackStack(ForesightRoutes.Home, inclusive = false)
                    }
                )
            }

            composable(ForesightRoutes.Notifications) {
                NotificationsScreen(userViewModel) { navController.popBackStack() }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavItemClick: (String) -> Unit
) {
    val darkBg = Color(0xFF070913)
    val accentPurple = Color(0xFF7C4DFF)
    val textSecondary = Color(0xFF9CA6BA)

    NavigationBar(
        containerColor = darkBg,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        NavigationBarItem(
            selected = currentRoute == ForesightRoutes.Home,
            onClick = { onNavItemClick(ForesightRoutes.Home) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentPurple,
                selectedTextColor = accentPurple,
                unselectedIconColor = textSecondary,
                unselectedTextColor = textSecondary,
                indicatorColor = accentPurple.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = currentRoute == ForesightRoutes.Scan,
            onClick = { onNavItemClick(ForesightRoutes.Scan) },
            icon = { Icon(Icons.Default.QrCode, contentDescription = "Scan") },
            label = { Text("Scan") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentPurple,
                selectedTextColor = accentPurple,
                unselectedIconColor = textSecondary,
                unselectedTextColor = textSecondary,
                indicatorColor = accentPurple.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = currentRoute == ForesightRoutes.History,
            onClick = { onNavItemClick(ForesightRoutes.History) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentPurple,
                selectedTextColor = accentPurple,
                unselectedIconColor = textSecondary,
                unselectedTextColor = textSecondary,
                indicatorColor = accentPurple.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = currentRoute == ForesightRoutes.Profile,
            onClick = { onNavItemClick(ForesightRoutes.Profile) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentPurple,
                selectedTextColor = accentPurple,
                unselectedIconColor = textSecondary,
                unselectedTextColor = textSecondary,
                indicatorColor = accentPurple.copy(alpha = 0.1f)
            )
        )
    }
}

private fun rememberPublicRoutes(): Set<String> {
    return setOf(
        ForesightRoutes.Splash,
        ForesightRoutes.Welcome,
        ForesightRoutes.Login
    )
}

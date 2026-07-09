package com.example.foresight

import androidx.compose.runtime.compositionLocalOf

data class AppStrings(
    val home: String,
    val scan: String,
    val history: String,
    val profile: String,
    val totalBalance: String,
    val recentTransactions: String,
    val seeAll: String,
    val goodEvening: String,
    val quickActions: String,
    val scanQr: String,
    val payContact: String,
    val bankTransfer: String
)

val EnglishStrings = AppStrings(
    home = "Home",
    scan = "Scan",
    history = "History",
    profile = "Profile",
    totalBalance = "Total Balance",
    recentTransactions = "Recent Transactions",
    seeAll = "See all",
    goodEvening = "Good Evening",
    quickActions = "Quick Actions",
    scanQr = "Scan QR",
    payContact = "Pay Contact",
    bankTransfer = "Bank Transfer"
)

val HindiStrings = AppStrings(
    home = "होम",
    scan = "स्कैन",
    history = "इतिहास",
    profile = "प्रोफ़ाइल",
    totalBalance = "कुल शेष",
    recentTransactions = "हाल के लेनदेन",
    seeAll = "सभी देखें",
    goodEvening = "शुभ संध्या",
    quickActions = "त्वरित कार्रवाई",
    scanQr = "QR स्कैन करें",
    payContact = "संपर्क को भुगतान करें",
    bankTransfer = "बैंक ट्रांसफर"
)

val LocalAppStrings = compositionLocalOf { EnglishStrings }

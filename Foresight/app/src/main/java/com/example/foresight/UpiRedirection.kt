package com.example.foresight

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

enum class UpiResult {
    SUCCESS, FAILED, CANCELLED, UNKNOWN
}

data class UpiApp(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

data class UpiDetails(
    val pa: String, // VPA
    val pn: String?, // Name
    val am: String?, // Amount
    val tn: String?, // Note
    val cu: String?, // Currency
    val mc: String? = null, // Merchant Code
    val tr: String? = null, // Transaction Ref
    val tid: String? = null, // Transaction ID
    val mode: String? = null, // Mode
    val orgid: String? = null, // Org ID
    val sign: String? = null, // Signature
    val url: String? = null, // Reference URL
    val purpose: String? = null, // Purpose code
    val mam: String? = null // Minimum amount
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiAppSelectorSheet(
    upiApps: List<UpiApp>,
    onAppSelected: (UpiApp) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111520),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
        ) {
            Text(
                text = "Choose UPI App",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            if (upiApps.isEmpty()) {
                Text(
                    text = "No UPI apps found on this device.",
                    color = Color(0xFF9CA6BA),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(upiApps) { app ->
                        UpiAppItem(app = app) {
                            onAppSelected(app)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpiAppItem(app: UpiApp, onClick: () -> Unit) {
    val isRecommended = app.packageName == "com.google.android.apps.nbu.paisa.user"
    val isPopular = app.packageName == "com.phonepe.app"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (isRecommended || isPopular) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color(0xFF20E3B2),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecommended) "Recommended" else "Popular",
                        color = Color(0xFF20E3B2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

object UpiManager {
    fun getInstalledUpiApps(context: Context): List<UpiApp> {
        val packageManager = context.packageManager
        val uri = Uri.parse("upi://pay")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val flags = PackageManager.MATCH_ALL

        val resolveInfoList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, flags)
        }

        val apps = resolveInfoList.map {
            val pkg = it.activityInfo.packageName
            val label = it.loadLabel(packageManager).toString()
            UpiApp(
                name = label,
                packageName = pkg,
                icon = it.loadIcon(packageManager)
            )
        }.distinctBy { it.packageName }

        return apps
    }

    fun createUpiUri(
        payeeVpa: String,
        payeeName: String,
        amount: String,
        transactionNote: String = "Payment",
        merchantCode: String? = null,
        transactionRef: String? = null,
        transactionId: String? = null,
        mode: String? = null,
        orgid: String? = null,
        sign: String? = null,
        url: String? = null,
        purpose: String? = null,
        mam: String? = null
    ): Uri {
        // Preserving original casing as per requirements
        val sanitizedVpa = payeeVpa.trim()
        val sanitizedName = payeeName.trim()
        val amountValue = amount.toDoubleOrNull()

        require(VPA_REGEX.matches(sanitizedVpa)) { "Invalid UPI ID format." }
        require(sanitizedName.isNotEmpty()) { "Payee name is required." }
        require(amountValue != null && amountValue.isFinite() && amountValue > 0.0 && amountValue <= MAX_UPI_AMOUNT) {
            "Invalid payment amount."
        }

        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amountValue)
        
        val safeNote = transactionNote.filter { it.isLetterOrDigit() || it == ' ' }
            .take(80)
            .ifEmpty { "Payment" }

        val builder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", sanitizedVpa)
            .appendQueryParameter("pn", sanitizedName)
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", safeNote)
            
        merchantCode?.let { builder.appendQueryParameter("mc", it) }
        transactionRef?.let { builder.appendQueryParameter("tr", it) }
        transactionId?.let { builder.appendQueryParameter("tid", it) }
        mode?.let { builder.appendQueryParameter("mode", it) }
        orgid?.let { builder.appendQueryParameter("orgid", it) }
        sign?.let { builder.appendQueryParameter("sign", it) }
        url?.let { builder.appendQueryParameter("url", it) }
        purpose?.let { builder.appendQueryParameter("purpose", it) }
        mam?.let { builder.appendQueryParameter("mam", it) }

        val uri = builder.build()
        android.util.Log.d("UpiDebug", "Generated UPI URI: $uri")
        return uri
    }

    fun parseUpiResponse(data: String?): UpiResult {
        if (data == null) return UpiResult.CANCELLED
        
        val statusMap = mutableMapOf<String, String>()
        data.split("&").forEach { param ->
            val parts = param.split("=")
            if (parts.size == 2) {
                statusMap[parts[0].lowercase()] = parts[1].lowercase()
            }
        }
        
        val status = statusMap["status"] ?: statusMap["txnstatus"] ?: data.lowercase()
        
        return when {
            status.contains("success") || status.contains("submitted") -> UpiResult.SUCCESS
            status.contains("fail") -> UpiResult.FAILED
            status.contains("cancel") -> UpiResult.CANCELLED
            else -> UpiResult.UNKNOWN
        }
    }

    fun parseUpiUri(uriStr: String): UpiDetails? {
        android.util.Log.d("UpiDebug", "Parsing QR URI: $uriStr")
        return try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme != "upi" || uri.host != "pay") {
                android.util.Log.e("UpiDebug", "Invalid URI Scheme/Host: ${uri.scheme}://${uri.host}")
                return null
            }
            // Removing .lowercase() to preserve case sensitivity if any
            val pa = uri.getQueryParameter("pa")?.trim() ?: return null
            if (!VPA_REGEX.matches(pa)) {
                android.util.Log.e("UpiDebug", "Invalid VPA format: $pa")
                return null
            }

            val am = uri.getQueryParameter("am")?.trim()
            if (!am.isNullOrEmpty()) {
                val amountValue = am.toDoubleOrNull()
                if (amountValue == null || !amountValue.isFinite() || amountValue <= 0.0 || amountValue > MAX_UPI_AMOUNT) {
                    android.util.Log.e("UpiDebug", "Invalid Amount in QR: $am")
                    return null
                }
            }

            val currency = uri.getQueryParameter("cu")?.trim()
            if (!currency.isNullOrEmpty() && currency.uppercase() != "INR") {
                android.util.Log.e("UpiDebug", "Invalid Currency in QR: $currency")
                return null
            }

            val details = UpiDetails(
                pa = pa, 
                pn = uri.getQueryParameter("pn"), 
                am = am, 
                tn = uri.getQueryParameter("tn"), 
                cu = currency,
                mc = uri.getQueryParameter("mc"),
                tr = uri.getQueryParameter("tr"),
                tid = uri.getQueryParameter("tid"),
                mode = uri.getQueryParameter("mode"),
                orgid = uri.getQueryParameter("orgid"),
                sign = uri.getQueryParameter("sign"),
                url = uri.getQueryParameter("url"),
                purpose = uri.getQueryParameter("purpose"),
                mam = uri.getQueryParameter("mam")
            )
            android.util.Log.d("UpiDebug", "Parsed QR Details: $details")
            details
        } catch (e: Exception) {
            android.util.Log.e("UpiDebug", "Parse Error", e)
            null
        }
    }

    fun launchUpiApp(
        context: Context,
        app: UpiApp,
        payeeVpa: String,
        payeeName: String,
        amount: String,
        transactionNote: String,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
        merchantCode: String? = null,
        transactionRef: String? = null,
        transactionId: String? = null,
        mode: String? = null,
        orgid: String? = null,
        sign: String? = null,
        url: String? = null,
        purpose: String? = null,
        mam: String? = null
    ) {
        try {
            val uri = createUpiUri(
                payeeVpa, 
                payeeName, 
                amount, 
                transactionNote, 
                merchantCode, 
                transactionRef, 
                transactionId,
                mode, 
                orgid, 
                sign,
                url,
                purpose,
                mam
            )
            
            android.util.Log.d("UpiDebug", "--- UPI Intent Launch ---")
            android.util.Log.d("UpiDebug", "URI: $uri")
            android.util.Log.d("UpiDebug", "App: ${app.name} (${app.packageName})")
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(app.packageName)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                launcher.launch(intent)
            } else {
                android.widget.Toast.makeText(context, "This UPI application is unavailable.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("UpiDebug", "Validation Error: ${e.message}")
            android.widget.Toast.makeText(context, e.message ?: "Invalid UPI payment details.", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("UpiDebug", "Launch Error", e)
            android.widget.Toast.makeText(context, "Could not launch ${app.name}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private const val MAX_UPI_AMOUNT = 1_000_000.0
    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,}@[a-zA-Z0-9.\\-_]{2,}$")
}

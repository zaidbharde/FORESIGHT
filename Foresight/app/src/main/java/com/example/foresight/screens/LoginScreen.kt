package com.example.foresight.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.data.auth.FirebaseAuthManager
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF070913)
private val CardBg = Color(0xFF111520)
private val AccentPurple = Color(0xFF7C4DFF)
private val TextSecondary = Color(0xFF9CA6BA)
private val AccentMint = Color(0xFF20E3B2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authManager = remember { FirebaseAuthManager() }
    
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1: Phone, 2: OTP
    var verificationId by remember { mutableStateOf("") }
    val resendTokenState = remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timerSeconds by remember { mutableIntStateOf(60) }

    LaunchedEffect(step) {
        if (step == 2) {
            timerSeconds = 60
            while (timerSeconds > 0) {
                delay(1000)
                timerSeconds--
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)
        ) {
            Text(
                text = if (step == 1) "Enter Phone Number" else "Verify OTP",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = if (step == 1) "We'll send you a verification code" else "Enter the code sent to +91 $phone",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            if (step == 1) {
                TextField(
                    value = phone,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 10) phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Mobile Number", color = TextSecondary) },
                    prefix = { Text("+91 ", color = Color.White) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    enabled = !isLoading
                )
            } else {
                TextField(
                    value = otp,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) otp = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("000000", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
                    enabled = !isLoading
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (timerSeconds > 0) "Resend code in ${timerSeconds}s" else "Didn't receive the code?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    
                    if (timerSeconds == 0) {
                        TextButton(
                            onClick = {
                                val currentActivity = activity
                                if (currentActivity == null) {
                                    errorMessage = "Unable to start verification on this screen."
                                    return@TextButton
                                }
                                isLoading = true
                                errorMessage = null
                                val callback = object : FirebaseAuthManager.OtpCallback {
                                    override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                        verificationId = vId
                                        resendTokenState.value = token
                                        isLoading = false
                                        timerSeconds = 60
                                        Toast.makeText(context, "OTP Sent", Toast.LENGTH_SHORT).show()
                                    }
                                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                        authManager.signInWithCredential(credential, object : FirebaseAuthManager.AuthCallback {
                                            override fun onSuccess(user: FirebaseUser?) {
                                                onLoginSuccess("+91$phone")
                                            }
                                            override fun onFailure(exception: Exception) {
                                                errorMessage = exception.message
                                                isLoading = false
                                            }
                                        })
                                    }
                                    override fun onVerificationFailed(exception: FirebaseException) {
                                        errorMessage = exception.message
                                        isLoading = false
                                    }
                                }

                                val token = resendTokenState.value
                                if (token != null) {
                                    authManager.resendOtp(currentActivity, "+91$phone", token, callback)
                                } else {
                                    authManager.sendOtp(currentActivity, "+91$phone", callback)
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text("Resend", color = AccentMint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage ?: "", color = Color.Red, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        errorMessage = "Unable to start verification on this screen."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    if (step == 1) {
                        authManager.sendOtp(
                            currentActivity,
                            "+91$phone",
                            object : FirebaseAuthManager.OtpCallback {
                                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                    verificationId = vId
                                    resendTokenState.value = token
                                    step = 2
                                    isLoading = false
                                }
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    authManager.signInWithCredential(credential, object : FirebaseAuthManager.AuthCallback {
                                        override fun onSuccess(user: FirebaseUser?) {
                                            onLoginSuccess("+91$phone")
                                        }
                                        override fun onFailure(exception: Exception) {
                                            errorMessage = exception.message
                                            isLoading = false
                                        }
                                    })
                                }
                                override fun onVerificationFailed(exception: FirebaseException) {
                                    errorMessage = exception.message
                                    isLoading = false
                                }
                            }
                        )
                    } else {
                        authManager.verifyOtp(
                            verificationId,
                            otp,
                            object : FirebaseAuthManager.AuthCallback {
                                override fun onSuccess(user: FirebaseUser?) {
                                    onLoginSuccess("+91$phone")
                                }
                                override fun onFailure(exception: Exception) {
                                    errorMessage = "Invalid OTP. Please try again."
                                    isLoading = false
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading && ((step == 1 && phone.length == 10) || (step == 2 && otp.length == 6)),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(if (step == 1) "Send OTP" else "Verify & Continue", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

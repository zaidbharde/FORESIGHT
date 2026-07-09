package com.example.foresight.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun sendOtp(
        activity: Activity,
        phoneNumber: String,
        callback: OtpCallback,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ) {
        if (!isValidPhoneNumber(phoneNumber)) {
            callback.onVerificationFailed(FirebaseException("Invalid phone number format."))
            return
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(
                object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        callback.onVerificationCompleted(credential)
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        callback.onVerificationFailed(exception)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        callback.onCodeSent(verificationId, token)
                    }
                }
            )
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun resendOtp(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken,
        callback: OtpCallback,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ) {
        if (!isValidPhoneNumber(phoneNumber)) {
            callback.onVerificationFailed(FirebaseException("Invalid phone number format."))
            return
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .setActivity(activity)
            .setForceResendingToken(resendToken)
            .setCallbacks(
                object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        callback.onVerificationCompleted(credential)
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        callback.onVerificationFailed(exception)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        callback.onCodeSent(verificationId, token)
                    }
                }
            )
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        verificationId: String,
        otp: String,
        callback: AuthCallback
    ) {
        if (verificationId.isBlank()) {
            callback.onFailure(IllegalArgumentException("Verification session expired. Request OTP again."))
            return
        }
        if (!otp.matches(Regex("^\\d{6}$"))) {
            callback.onFailure(IllegalArgumentException("Invalid OTP format."))
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        signInWithCredential(credential, callback)
    }

    fun signInWithCredential(
        credential: PhoneAuthCredential,
        callback: AuthCallback
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                callback.onSuccess(result.user)
            }
            .addOnFailureListener { exception ->
                callback.onFailure(exception)
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun getCurrentUserPhoneNumber(): String? = auth.currentUser?.phoneNumber

    fun isLoggedIn(): Boolean = auth.currentUser != null

    interface OtpCallback {
        fun onCodeSent(
            verificationId: String,
            resendToken: PhoneAuthProvider.ForceResendingToken
        )

        fun onVerificationCompleted(credential: PhoneAuthCredential)

        fun onVerificationFailed(exception: FirebaseException)
    }

    interface AuthCallback {
        fun onSuccess(user: FirebaseUser?)

        fun onFailure(exception: Exception)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60L
        val PHONE_E164_REGEX: Regex = Regex("^\\+[1-9]\\d{7,14}$")
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return PHONE_E164_REGEX.matches(phoneNumber)
    }
}

package com.example.foresight.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foresight.R
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF070913)
private val AccentPurple = Color(0xFF7C4DFF)
private val TextSecondary = Color(0xFF9CA6BA)

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 0.8f,
            animationSpec = tween(1000, easing = { OvershootInterpolator(4f).getInterpolation(it) })
        )
        delay(1000)
        onSplashFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(120.dp).scale(scale.value)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "FORESIGHT",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }
    }
}

private class OvershootInterpolator(val tension: Float) {
    fun getInterpolation(input: Float): Float {
        var t = input - 1.0f
        return t * t * ((tension + 1) * t + tension) + 1.0f
    }
}

@Composable
fun WelcomeScreen(onGetStartedClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Secure Your Future with AI",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Experience the next generation of fintech with AI-powered fraud detection and secure UPI payments.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.weight(1.5f))
        
        Button(
            onClick = onGetStartedClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

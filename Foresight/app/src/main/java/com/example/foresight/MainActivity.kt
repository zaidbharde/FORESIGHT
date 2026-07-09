package com.example.foresight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foresight.Navigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            val userViewModel: UserViewModel = viewModel()
            val settings by userViewModel.settings.collectAsState()
            
            val darkTheme = when (settings.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            
            val strings = when (settings.language) {
                AppLanguage.ENGLISH -> EnglishStrings
                AppLanguage.HINDI -> HindiStrings
            }
            
            CompositionLocalProvider(LocalAppStrings provides strings) {
                ForesightTheme(darkTheme = darkTheme) {
                    Navigation()
                }
            }
        }
    }
}

@Composable
private fun ForesightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF7C4DFF), // Accent Purple
            onPrimary = Color.White,
            secondary = Color(0xFF20E3B2), // Accent Mint
            onSecondary = Color(0xFF00251F),
            background = Color(0xFF070913),
            surface = Color(0xFF111520),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF171C2A),
            onSurfaceVariant = Color(0xFF9CA6BA)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006C5B),
            secondary = Color(0xFF425BA8),
            tertiary = Color(0xFF7A5700),
            background = Color(0xFFF8FAFD),
            surface = Color(0xFFFFFFFF)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

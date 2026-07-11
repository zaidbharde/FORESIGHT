package com.example.foresight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foresight.Navigation
import com.example.foresight.ui.theme.ForesightTheme

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

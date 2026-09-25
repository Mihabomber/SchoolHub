package com.school.hub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.hub.core.ui.theme.SchoolHubTheme
import com.school.hub.navigation.SchoolHubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SchoolApp).container
        setContent {
            val mode by container.settings.themeMode.collectAsStateWithLifecycle()
            val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

            // Один раз спрашиваем разрешение на уведомления (Android 13+)
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                container.reminders.requestReschedule()
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && !container.settings.askedNotifications &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    container.settings.askedNotifications = true
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SchoolHubTheme(darkTheme = dark, dynamicColor = dynamic) { SchoolHubApp() }
        }
    }
}

package com.school.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.school.hub.core.ui.theme.SchoolHubTheme
import com.school.hub.navigation.SchoolHubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SchoolHubTheme { SchoolHubApp() } }
    }
}

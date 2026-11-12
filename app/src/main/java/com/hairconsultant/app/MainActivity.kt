package com.hairconsultant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hairconsultant.app.ui.navigation.HairConsultantNavHost
import com.hairconsultant.app.ui.theme.HairConsultantTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HairConsultantApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HairConsultantAppRoot(container = container)
        }
    }
}

@Composable
private fun HairConsultantAppRoot(container: com.hairconsultant.app.di.AppContainer) {
    val darkModeChoice by container.themePreference.darkMode.collectAsState()
    HairConsultantTheme(darkTheme = darkModeChoice ?: isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HairConsultantNavHost(container = container)
        }
    }
}

package com.hairconsultant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")

    data object Home : Screen("home")
    data object FaceScan : Screen("face_scan")
    data object ImageUpload : Screen("image_upload")
    data object Profile : Screen("profile")

    companion object {
        /** Screens where a photo/analysis is in progress and shouldn't show chrome around it. */
        val fullScreenRoutes = setOf(FaceScan.route)
    }
}

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.FaceScan, "Face Scan", Icons.Filled.CameraAlt),
    BottomNavItem(Screen.ImageUpload, "Upload", Icons.Filled.UploadFile),
    BottomNavItem(Screen.Profile, "Profile", Icons.Filled.AccountCircle)
)

/** The floating AI chatbot button follows the user everywhere except Profile/Settings. */
val chatButtonRoutes = setOf(Screen.Home.route, Screen.FaceScan.route, Screen.ImageUpload.route)

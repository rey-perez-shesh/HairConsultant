package com.hairconsultant.app.ui.navigation

import android.net.Uri
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

    /** Hair color try-on. Not a tab: opened from Home, Face Scan, or a photo on Upload ([PHOTO_ARG]). */
    data object ColorStudio : Screen("color_studio?photo={photo}") {
        const val PHOTO_ARG = "photo"

        /** [photo] null is the live camera. */
        fun route(photo: Uri? = null): String =
            if (photo == null) "color_studio" else "color_studio?$PHOTO_ARG=${Uri.encode(photo.toString())}"
    }

    companion object {
        /** Screens where a photo/analysis is in progress and shouldn't show chrome around it. */
        val fullScreenRoutes = setOf(FaceScan.route, ColorStudio.route)
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

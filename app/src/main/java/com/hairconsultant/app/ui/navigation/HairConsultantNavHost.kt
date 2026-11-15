package com.hairconsultant.app.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hairconsultant.app.di.AppContainer
import com.hairconsultant.app.di.ViewModelFactory
import com.hairconsultant.app.ui.auth.LoginScreen
import com.hairconsultant.app.ui.auth.LoginViewModel
import com.hairconsultant.app.ui.auth.RegisterScreen
import com.hairconsultant.app.ui.auth.RegisterViewModel
import com.hairconsultant.app.ui.components.HairConsultantBottomBar
import com.hairconsultant.app.ui.facescan.FaceScanScreen
import com.hairconsultant.app.ui.facescan.FaceScanViewModel
import com.hairconsultant.app.ui.home.HomeScreen
import com.hairconsultant.app.ui.home.HomeViewModel
import com.hairconsultant.app.ui.imageupload.ImageUploadScreen
import com.hairconsultant.app.ui.imageupload.ImageUploadViewModel
import com.hairconsultant.app.ui.profile.ProfileScreen
import com.hairconsultant.app.ui.theme.ImageWellDark
import com.hairconsultant.app.ui.profile.ProfileViewModel

@Composable
fun HairConsultantNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val currentUser by container.authRepository.currentUser.collectAsState()
    val startDestination = if (currentUser != null) Screen.Home.route else Screen.Login.route

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.screen.route == currentRoute } &&
        currentRoute !in Screen.fullScreenRoutes

    Scaffold(
        // Camera screens stay dark in both themes, including the strip under the status bar.
        containerColor = if (currentRoute in Screen.fullScreenRoutes) ImageWellDark else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                HairConsultantBottomBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        // Tab screens draw their shell header under the status bar themselves; every other screen
        // gets the full inset, consumed so nested insets padding doesn't add it a second time.
        // Only the light auth screens want dark status-bar icons.
        val view = LocalView.current
        // Read from the applied theme, not the system flag: Profile's toggle can override the system.
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val lightChrome = !darkTheme && currentRoute in listOf(Screen.Login.route, Screen.Register.route)
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightChrome
                    isAppearanceLightNavigationBars = lightChrome
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (showBottomBar) {
                Modifier.padding(bottom = padding.calculateBottomPadding())
            } else {
                Modifier.padding(padding).consumeWindowInsets(padding)
            }
        ) {
            composable(Screen.Login.route) {
                val viewModel: LoginViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    LoginViewModel(c.authRepository, c.userRepository)
                })
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    },
                    onCreateAccountClick = { navController.navigate(Screen.Register.route) }
                )
            }
            composable(Screen.Register.route) {
                val viewModel: RegisterViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    RegisterViewModel(c.authRepository, c.userRepository)
                })
                RegisterScreen(
                    viewModel = viewModel,
                    onRegisterSuccess = {
                        navController.navigate(Screen.Login.route) { popUpTo(0) }
                    },
                    onBackToLoginClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    HomeViewModel(
                        c.haircutRepository,
                        c.geminiChatRepository,
                        c.chatBotController,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository
                    )
                })
                LaunchedEffect(Unit) { viewModel.activateChat() }
                HomeScreen(viewModel = viewModel)
            }
            composable(Screen.FaceScan.route) {
                val viewModel: FaceScanViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    FaceScanViewModel(
                        c.faceAnalyzer,
                        c.hairstyleRecommender,
                        c.faceLandmarkStore,
                        c.geminiChatRepository,
                        c.chatBotController,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository
                    )
                })
                LaunchedEffect(Unit) { viewModel.activateChat() }
                FaceScanScreen(viewModel = viewModel)
            }
            composable(Screen.ImageUpload.route) {
                val viewModel: ImageUploadViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    ImageUploadViewModel(
                        c.faceAnalyzer,
                        c.hairstyleRecommender,
                        c.geminiImageRepository,
                        c.geminiChatRepository,
                        c.chatBotController,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository,
                        c.mediaStorageRepository
                    )
                })
                LaunchedEffect(Unit) { viewModel.activateChat() }
                ImageUploadScreen(viewModel = viewModel)
            }
            composable(Screen.Profile.route) {
                val viewModel: ProfileViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    ProfileViewModel(c.authRepository, c.userRepository, c.consultationRepository, c.feedbackRepository)
                })
                ProfileScreen(
                    viewModel = viewModel,
                    isDarkTheme = darkTheme,
                    onToggleTheme = { container.themePreference.setDarkMode(!darkTheme) },
                    onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) } }
                )
            }
        }
    }
}

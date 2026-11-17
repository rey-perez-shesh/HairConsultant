package com.hairconsultant.app.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hairconsultant.app.di.AppContainer
import com.hairconsultant.app.di.ViewModelFactory
import com.hairconsultant.app.ui.auth.LoginScreen
import com.hairconsultant.app.ui.auth.LoginViewModel
import com.hairconsultant.app.ui.auth.RegisterScreen
import com.hairconsultant.app.ui.auth.RegisterViewModel
import com.hairconsultant.app.ui.components.HairConsultantBottomBar
import com.hairconsultant.app.ui.facescan.FaceScanScreen
import com.hairconsultant.app.ui.facescan.FaceScanViewModel
import com.hairconsultant.app.ui.haircolor.ColorStudioScreen
import com.hairconsultant.app.ui.haircolor.ColorStudioViewModel
import com.hairconsultant.app.ui.home.HomeScreen
import com.hairconsultant.app.ui.home.HomeViewModel
import com.hairconsultant.app.ui.imageupload.ImageUploadScreen
import com.hairconsultant.app.ui.imageupload.ImageUploadViewModel
import com.hairconsultant.app.ui.profile.ProfileScreen
import com.hairconsultant.app.ui.theme.EaseOutStrong
import com.hairconsultant.app.ui.theme.ImageWellDark
import com.hairconsultant.app.ui.theme.LocalShopColors
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
        // Login and Register carry the shell header up under the status bar the same way.
        containerColor = when (currentRoute) {
            in Screen.fullScreenRoutes -> ImageWellDark
            Screen.Login.route, Screen.Register.route -> LocalShopColors.current.shell
            else -> MaterialTheme.colorScheme.background
        },
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
        // Every screen puts the dark shell (or a camera) behind the system bars, so their icons stay light.
        val view = LocalView.current
        // Read from the applied theme, not the system flag: Profile's toggle can override the system.
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
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
            // Going to and from the Color Studio is quicker than the default tab cross-fade: its
            // camera only starts once the transition has finished, so a slow one is a black screen.
            val openColorStudio = { photo: Uri? -> navController.navigate(Screen.ColorStudio.route(photo)) }
            val exitToStudio: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
                if (targetState.destination.route == Screen.ColorStudio.route) fadeOut(tween(220)) else null
            }
            val returnFromStudio: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
                if (initialState.destination.route == Screen.ColorStudio.route) fadeIn(tween(160)) else null
            }

            composable(
                route = Screen.ColorStudio.route,
                arguments = listOf(
                    navArgument(Screen.ColorStudio.PHOTO_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    fadeIn(tween(220, easing = EaseOutStrong)) + scaleIn(tween(220, easing = EaseOutStrong), initialScale = 0.96f)
                },
                exitTransition = { fadeOut(tween(160)) },
                popExitTransition = { fadeOut(tween(160)) }
            ) { entry ->
                val viewModel: ColorStudioViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    ColorStudioViewModel(c.authRepository, c.consultationRepository, c.mediaStorageRepository)
                })
                ColorStudioScreen(
                    viewModel = viewModel,
                    photoUri = entry.arguments?.getString(Screen.ColorStudio.PHOTO_ARG)?.let(Uri::parse),
                    onClose = { navController.popBackStack() },
                    onViewLooks = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Home.route, exitTransition = exitToStudio, popEnterTransition = returnFromStudio) {
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
                HomeScreen(viewModel = viewModel, onOpenColorStudio = { openColorStudio(null) })
            }
            composable(Screen.FaceScan.route, exitTransition = exitToStudio, popEnterTransition = returnFromStudio) {
                val viewModel: FaceScanViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    FaceScanViewModel(
                        c.faceAnalyzer,
                        c.hairstyleRecommender,
                        c.faceLandmarkStore,
                        c.geminiChatRepository,
                        c.chatBotController,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository,
                        c.scanPhotoStore
                    )
                })
                LaunchedEffect(Unit) { viewModel.activateChat() }
                FaceScanScreen(viewModel = viewModel, onOpenColorStudio = { openColorStudio(null) })
            }
            composable(Screen.ImageUpload.route, exitTransition = exitToStudio, popEnterTransition = returnFromStudio) {
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
                        c.mediaStorageRepository,
                        c.scanPhotoStore
                    )
                })
                LaunchedEffect(Unit) { viewModel.activateChat() }
                ImageUploadScreen(viewModel = viewModel, onOpenColorStudio = openColorStudio)
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

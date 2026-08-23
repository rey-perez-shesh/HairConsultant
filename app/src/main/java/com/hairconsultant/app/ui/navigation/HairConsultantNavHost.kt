package com.hairconsultant.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.hairconsultant.app.ui.profile.ProfileViewModel

@Composable
fun HairConsultantNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val currentUser by container.authRepository.currentUser.collectAsState()
    val startDestination = if (currentUser != null) Screen.Home.route else Screen.Login.route

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.screen.route == currentRoute }

    Scaffold(
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
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) {
                val viewModel: LoginViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    LoginViewModel(c.authRepository)
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
                    HomeViewModel(c.haircutRepository)
                })
                HomeScreen(viewModel = viewModel)
            }
            composable(Screen.FaceScan.route) {
                val viewModel: FaceScanViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    FaceScanViewModel(
                        c.faceAnalyzer,
                        c.haircutRepository,
                        c.faceLandmarkStore,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository
                    )
                })
                FaceScanScreen(viewModel = viewModel)
            }
            composable(Screen.ImageUpload.route) {
                val viewModel: ImageUploadViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    ImageUploadViewModel(
                        c.faceAnalyzer,
                        c.haircutRepository,
                        c.geminiImageRepository,
                        c.authRepository,
                        c.userRepository,
                        c.consultationRepository,
                        c.mediaStorageRepository
                    )
                })
                ImageUploadScreen(viewModel = viewModel)
            }
            composable(Screen.Profile.route) {
                val viewModel: ProfileViewModel = viewModel(factory = ViewModelFactory(container) { c ->
                    ProfileViewModel(c.authRepository, c.userRepository, c.consultationRepository, c.feedbackRepository)
                })
                ProfileScreen(
                    viewModel = viewModel,
                    onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) } }
                )
            }
        }
    }
}

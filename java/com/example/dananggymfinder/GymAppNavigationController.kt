package com.example.dananggymfinder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dananggymfinder.ui.screens.LoginScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymAppNavigationController(gymViewModel: GymViewModel = viewModel()) {
    val loggedInUser by gymViewModel.loggedInUser.observeAsState()
    var currentScreen by remember { mutableStateOf(if (loggedInUser == null) Screen.Login else Screen.GymList) }
    val selectedGym = gymViewModel.selectedGym
    LaunchedEffect(selectedGym) {
        if (selectedGym != null && currentScreen == Screen.GymList) {
            currentScreen = Screen.GymDetail
        }
    }
    LaunchedEffect(loggedInUser) {
        currentScreen = if (loggedInUser == null) Screen.Login else Screen.GymList
        if (loggedInUser != null && currentScreen == Screen.GymList) { // Fetch gyms only if navigating to GymList after login
            gymViewModel.fetchGyms()
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen != Screen.Login && currentScreen != Screen.Register) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                Screen.GymList -> "Phòng Gym Đà Nẵng"
                                Screen.GymDetail -> gymViewModel.selectedGym?.name ?: "Chi Tiết"
                                else -> "Đà Nẵng Gym Finder"
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentScreen == Screen.GymDetail) {
                            IconButton(onClick = {
                                currentScreen = Screen.GymList
                                gymViewModel.clearSelectedGym()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                            }
                        }
                    },
                    actions = {
                        if (loggedInUser != null) {
                            if (currentScreen == Screen.GymList || currentScreen == Screen.GymDetail) {
                                TextButton(onClick = {
                                    gymViewModel.logout()
                                }) {
                                    Text("Đăng xuất")
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.Login -> LoginScreen(
                    gymViewModel = gymViewModel,
                    onNavigateToRegister = { currentScreen = Screen.Register },
                    onLoginSuccess = {}
                )
                Screen.Register -> RegisterScreen(
                    gymViewModel = gymViewModel,
                    onNavigateToLogin = { currentScreen = Screen.Login },
                    onRegisterSuccess = {}
                )
                Screen.GymList -> GymListScreen(
                    gymViewModel = gymViewModel,
                    onGymClick = { gymId ->
                        gymViewModel.fetchGymDetail(gymId)
//                        currentScreen = Screen.GymDetail
                    }
                )
                Screen.GymDetail -> {
                    if (gymViewModel.selectedGym != null) {
                        GymDetailScreen(gymViewModel = gymViewModel)
                    } else {
                        LaunchedEffect(Unit) {
                            currentScreen = Screen.GymList
                        }
                    }
                }
            }
        }
    }
}
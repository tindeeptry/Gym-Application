package com.example.dananggymfinder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GymListScreen(gymViewModel: GymViewModel, onGymClick: (Int) -> Unit) {
    val gyms = gymViewModel.gymList
    val isLoading = gymViewModel.isLoadingGyms
    val errorMessage = gymViewModel.gymListErrorMessage
    val loggedInUser by gymViewModel.loggedInUser.observeAsState()

    var expanded by remember { mutableStateOf(false) }
    var selectedSortOption by remember { mutableStateOf("Đánh giá cao nhất") }

    // Sắp xếp danh sách gym theo selectedSortOption
    val sortedGyms = when (selectedSortOption) {
        "Đánh giá cao nhất" -> gyms.sortedByDescending { it.rating ?: 0f }
        "Đánh giá thấp nhất" -> gyms.sortedBy { it.rating ?: 0f }
        else -> gyms
    }

    if (isLoading) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
        }
        return
    }

    errorMessage?.let {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (gyms.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Không có phòng gym nào để hiển thị.")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Nút sắp xếp
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.height(48.dp)
            ) {
                Text("Sắp xếp: $selectedSortOption")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Mở menu sắp xếp")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Đánh giá cao nhất") },
                    onClick = {
                        selectedSortOption = "Đánh giá cao nhất"
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Đánh giá thấp nhất") },
                    onClick = {
                        selectedSortOption = "Đánh giá thấp nhất"
                        expanded = false
                    }
                )
            }
        }

        // Danh sách phòng gym
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = sortedGyms, key = { it.id }) { gym ->
                GymListItem(
                    gym = gym,
                    isFavorite = gymViewModel.favoriteGymIds.contains(gym.id),
                    onFavoriteClick = {
                        if (loggedInUser != null) {
                            gymViewModel.toggleFavorite(gym.id)
                        }
                    },
                    onClick = { onGymClick(gym.id) },
                    showFavoriteButton = loggedInUser != null
                )
            }
        }
    }
}


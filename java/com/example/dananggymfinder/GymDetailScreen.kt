package com.example.dananggymfinder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun GymDetailScreen(gymViewModel: GymViewModel) {
    val gym by rememberUpdatedState(newValue = gymViewModel.selectedGym)
    val isLoading = gymViewModel.isLoadingDetail
    val errorMessage = gymViewModel.gymDetailErrorMessage
    val reviews = gymViewModel.reviewsForSelectedGym
    val isFavorite = gym?.id?.let { gymViewModel.favoriteGymIds.contains(it) } ?: false
    val context = LocalContext.current
    var showReviewDialog by remember { mutableStateOf(false) }
    val loggedInUser by gymViewModel.loggedInUser.observeAsState()

    if (isLoading) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator() }
        return
    }
    errorMessage?.let {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text(it, color = MaterialTheme.colorScheme.error) }
        return
    }
    val currentGym = gym ?: run {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Không có thông tin chi tiết.") }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                AsyncImage(
                    model = currentGym.imageUrl ?: "https://placehold.co/600x300/E0E0E0/757575?text=Gym+Detail",
                    contentDescription = "Hình ảnh ${currentGym.name}",
                    modifier = Modifier.fillMaxWidth().height(250.dp).aspectRatio(16f/9f),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = currentGym.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    if (loggedInUser != null) {
                        IconButton(onClick = { gymViewModel.toggleFavorite(currentGym.id) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Yêu thích",
                                tint = if (isFavorite) MaterialTheme.colorScheme.error else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = "Rating", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${String.format(Locale.US, "%.1f", currentGym.rating ?: 0.0f)}/5.0 (${currentGym.totalReviews ?: 0} đánh giá)", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Địa chỉ: ${currentGym.address}", style = MaterialTheme.typography.bodyLarge)
                currentGym.phoneNumber?.let { Text("SĐT: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                currentGym.openingHours?.let { Text("Giờ mở cửa: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }

                Button(
                    onClick = {
                        var gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(currentGym.address + ", Đà Nẵng")}")
                        if (currentGym.latitude != null && currentGym.longitude != null) {
                            gmmIntentUri = Uri.parse("geo:${currentGym.latitude},${currentGym.longitude}?q=${Uri.encode(currentGym.address)}")
                        }
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Không tìm thấy ứng dụng Google Maps", Toast.LENGTH_LONG).show()
// Typo: in word 'đương' - Fixed to 'đường'
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(currentGym.address + ", Đà Nẵng")}"))
                            try {
                                context.startActivity(webIntent)
                            } catch (e2: ActivityNotFoundException){
                                Toast.makeText(context, "Không thể mở bản đồ", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Place, contentDescription = "Chỉ đường", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Chỉ đường trên Google Maps")
                }
            }
        }

        currentGym.description?.takeIf { it.isNotBlank() }?.let {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Mô tả", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        currentGym.facilities?.takeIf { it.isNotEmpty() }?.let {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Tiện ích", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    it.forEach { facility -> Text("• $facility", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Đánh giá (${reviews.size})", style = MaterialTheme.typography.titleLarge)
                    if (loggedInUser != null) {
                        TextButton(onClick = { showReviewDialog = true }) {
                            Text("Viết đánh giá")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (reviews.isEmpty()) {
            item {
                Text(
                    "Chưa có đánh giá nào.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(items = reviews, key = { it.id }) { review ->
                ReviewItem(review, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showReviewDialog) {
        AddReviewDialog(
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                gymViewModel.postReview(currentGym.id, rating, comment)
                showReviewDialog = false
            }
        )
    }
}
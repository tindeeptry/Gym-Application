package com.example.dananggymfinder

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AddReviewDialog(onDismiss: () -> Unit, onSubmit: (rating: Int, comment: String) -> Unit) {
    var rating by remember { mutableIntStateOf(0) } // Prefer mutableIntStateOf
    var comment by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Viết đánh giá của bạn") },
        text = {
            Column {
                Text("Chạm để đánh giá:")
                Row(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..5).forEach { starIndex ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Star $starIndex",
                            tint = if (starIndex <= rating) Color(0xFFFFC107) else Color.Gray,
                            modifier = Modifier.size(40.dp).clickable { rating = starIndex }.padding(4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Bình luận (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 150.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rating > 0) {
                        onSubmit(rating, comment)
                    } else {
                        Toast.makeText(context, "Vui lòng chọn số sao đánh giá.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("Gửi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
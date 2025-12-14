package com.example.dananggymfinder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dananggymfinder.ui.theme.DaNangGymFinderTheme
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// --- DATA MODELS ---
data class User(
    val id: Int,
    val username: String,
    val email: String,
    @SerializedName("full_name")
    val fullName: String?
)

data class AuthResponse (
    val message: String,
    val user: User?,
    )

data class Gym (
    val id: Int,
    val name: String,
    val address: String,
    val description: String?,
    @SerializedName("phone_number")
    val phoneNumber: String?,
    @SerializedName("opening_hours")
    val openingHours: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    var rating: Float?,
    @SerializedName("total_reviews")
    var totalReviews: Int?,
    val facilities: List<String>?
)

data class Review(
    val id: Int,
    @SerializedName("gym_id")
    val gymId: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_username")
    val userUsername: String,
    val rating: Int,
    val comment: String?,
    @SerializedName("created_at")
    val createdAt: String
)

data class FavoriteRequest(
    val gymId: Int
)

data class PostReviewRequest(
    val userId: Int,
    val rating: Int,
    val comment: String?
)


// --- RETROFIT API SERVICE ---
interface GymApiService {
    companion object {
        private const val BASE_URL = "http://10.0.2.2:3000/api/"
        fun create(): GymApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GymApiService::class.java)
        }
    }

    @POST("auth/register")
    suspend fun register(@Body user: Map<String, String>): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body credentials: Map<String, String>): AuthResponse

    @GET("gyms")
    suspend fun getGyms(): List<Gym>

    @GET("gyms/{id}")
    suspend fun getGymDetail(@Path("id") gymId: Int): Gym

    @GET("gyms/{gymId}/reviews")
    suspend fun getGymReviews(@Path("gymId") gymId: Int): List<Review>

    @POST("gyms/{gymId}/reviews")
    suspend fun postReview(@Path("gymId") gymId: Int, @Body reviewData: PostReviewRequest): Review

    @GET("users/{userId}/favorites")
    suspend fun getUserFavorites(@Path("userId") userId: Int): List<Gym>

    @POST("users/{userId}/favorites")
    suspend fun addFavorite(@Path("userId") userId: Int, @Body favoriteRequest: FavoriteRequest): GenericResponse

    @DELETE("users/{userId}/favorites/{gymId}")
    suspend fun removeFavorite(@Path("userId") userId: Int, @Path("gymId") gymId: Int): GenericResponse
}

data class GenericResponse(val message: String) // Used for simple API responses, including error messages

// --- VIEWMODEL ---
class GymViewModel(private val apiService: GymApiService = GymApiService.create()) : ViewModel() {
    private val _loggedInUser = MutableLiveData<User?>(null)
    val loggedInUser: LiveData<User?> = _loggedInUser

    private val _authErrorMessage = MutableLiveData<String?>(null)
    val authErrorMessage: LiveData<String?> = _authErrorMessage

    var gymList by mutableStateOf<List<Gym>>(emptyList())
        private set
    var isLoadingGyms by mutableStateOf(false)
        private set
    var gymListErrorMessage by mutableStateOf<String?>(null)
        private set

    var selectedGym by mutableStateOf<Gym?>(null)
        private set
    var isLoadingDetail by mutableStateOf(false)
        private set
    var gymDetailErrorMessage by mutableStateOf<String?>(null)
        private set

    var reviewsForSelectedGym by mutableStateOf<List<Review>>(emptyList())
        private set
    private var isLoadingReviews by mutableStateOf(false)

    var favoriteGymIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    private val gson = Gson() // Instance of Gson for parsing error bodies
    fun login(username: String, password: String) {
        Log.d("GymViewModel_Login", "Attempting login for user: $username") // Log input
        viewModelScope.launch {
            _authErrorMessage.value = null
            try {
                val response = apiService.login(mapOf("username" to username, "password" to password))
                Log.d("GymViewModel_Login", "Login API response: User: ${response.user}, Message: ${response.message}")
                if (response.user != null) {
                    _loggedInUser.value = response.user
                    fetchUserFavorites(response.user.id)
                } else {
                // Nếu API trả về user null nhưng có message (ví dụ: sai mật khẩu từ backend đã check)
                    _authErrorMessage.value = response.message
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("GymViewModel_Login", "HttpException: ${e.code()} - $errorBody", e)
                try {
                    val errorResponse = gson.fromJson(errorBody, GenericResponse::class.java)
                    _authErrorMessage.value = "Đăng nhập thất bại: ${errorResponse?.message ?: e.message()}"
                } catch (parseEx: Exception) {
                    Log.e("GymViewModel_Login", "Error parsing error body: ", parseEx)
                    _authErrorMessage.value = "Đăng nhập thất bại: ${e.code()} (Không thể đọc chi tiết lỗi)"
                }
            } catch (e: Exception) {
                Log.e("GymViewModel_Login", "Generic Exception: ", e)
                _authErrorMessage.value = "Lỗi kết nối hoặc lỗi không xác định: ${e.message}"
            }
        }
    }

    fun register(username: String, password: String, email: String, fullName: String) {
        viewModelScope.launch {
            _authErrorMessage.value = null
            try {
                val response = apiService.register(mapOf("username" to username, "password" to password, "email" to email, "full_name" to fullName))
                if (response.user != null) {
                    _loggedInUser.value = response.user
                    fetchUserFavorites(response.user.id)
                } else {
                    _authErrorMessage.value = response.message
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                try {
                    val errorResponse = gson.fromJson(errorBody, GenericResponse::class.java)
                    _authErrorMessage.value = "Đăng ký thất bại: ${errorResponse?.message ?: e.message()}"
                } catch (parseEx: Exception) {
                    _authErrorMessage.value = "Đăng ký thất bại: ${e.code()} (Không thể đọc chi tiết lỗi)"
                }
            } catch (e: Exception) {
                _authErrorMessage.value = "Lỗi kết nối hoặc lỗi không xác định: ${e.message}"
            }
        }
    }

    fun logout() {
        _loggedInUser.value = null
        favoriteGymIds = emptySet()
    }

    fun fetchGyms() {
        isLoadingGyms = true
        gymListErrorMessage = null
        viewModelScope.launch {
            try {
                gymList = apiService.getGyms()
            } catch (e: Exception) {
                gymListErrorMessage = "Lỗi tải danh sách phòng gym: ${e.message}"
            } finally {
                isLoadingGyms = false
            }
        }
    }

    fun fetchGymDetail(gymId: Int) {
        isLoadingDetail = true
        gymDetailErrorMessage = null
        viewModelScope.launch {
           try {
                selectedGym = apiService.getGymDetail(gymId)
                fetchGymReviews(gymId)
            } catch (e: Exception) {
                gymDetailErrorMessage = "Lỗi tải chi tiết phòng gym: ${e.message}"
                selectedGym = null
            } finally {
                isLoadingDetail = false
            }
        }
    }

    private fun fetchGymReviews(gymId: Int) {
        isLoadingReviews = true
        viewModelScope.launch {
            try {
                reviewsForSelectedGym = apiService.getGymReviews(gymId)
            } catch (e: Exception) {
                reviewsForSelectedGym = emptyList() // Clear reviews on error
            } finally {
                isLoadingReviews = false
            }
        }
    }

    fun postReview(gymId: Int, rating: Int, comment: String?) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            try {
                val newReview = apiService.postReview(gymId, PostReviewRequest(currentUser.id, rating, comment))
                reviewsForSelectedGym = listOf(newReview) + reviewsForSelectedGym
                val currentGym = selectedGym
                if(currentGym != null) {
                    fetchGymDetail(currentGym.id) // Refresh gym details to update average rating
                }
            } catch (e: Exception) {
                gymDetailErrorMessage = "Lỗi gửi đánh giá: ${e.message}"
            }
        }
    }

    private fun fetchUserFavorites(userId: Int) {
        viewModelScope.launch {
            try {
                val favorites = apiService.getUserFavorites(userId)
                favoriteGymIds = favorites.map { it.id }.toSet()
            } catch (e: Exception) {
// Handle error silently or log
                Log.e("GymViewModel", "Error fetching user favorites: ${e.message}")
            }
        }
    }

    fun toggleFavorite(gymId: Int) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            try {
                if (favoriteGymIds.contains(gymId)) {
                    apiService.removeFavorite(currentUser.id, gymId)
                    favoriteGymIds = favoriteGymIds - gymId
                } else {
                    apiService.addFavorite(currentUser.id, FavoriteRequest(gymId))
                    favoriteGymIds = favoriteGymIds + gymId
                }
                // Resort gymList: favorites first
                gymList = gymList.sortedByDescending { favoriteGymIds.contains(it.id) }
            } catch (e: Exception) {
                Log.e("GymViewModel", "Error toggling favorite: ${e.message}")
            }
        }
    }

    fun clearSelectedGym() {
        selectedGym = null
        reviewsForSelectedGym = emptyList()
    }
    fun clearAuthError() {
        _authErrorMessage.value = null
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DaNangGymFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GymAppNavigationController()
                }
            }
        }
    }
}

// --- NAVIGATION ---
enum class Screen(val route: String) {
    Login("login"),
    Register("register"),
    GymList("gym_list"),
    GymDetail("gym_detail")
}




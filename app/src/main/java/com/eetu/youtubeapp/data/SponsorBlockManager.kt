package com.eetu.youtubeapp.data

import android.content.Context
import com.eetu.youtubeapp.data.model.Segment
import com.eetu.youtubeapp.data.model.VideoSegmentsResponse
import com.eetu.youtubeapp.data.remote.SponsorBlockApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SponsorBlockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("sb_manager_prefs", Context.MODE_PRIVATE)
    private val scriptFile = File(context.filesDir, "sb_latest.js")
    
    init {
        // Ensure we have a userID as per SB requirements
        if (prefs.getString("userID", null) == null) {
            val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val randomId = (1..32)
                .map { allowedChars.random() }
                .joinToString("")
            prefs.edit().putString("userID", randomId).apply()
        }
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val apiService: SponsorBlockApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", getUserAgent().ifEmpty { 
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                    })
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SponsorBlockApiService::class.java)
    }

    private val githubApiService: SponsorBlockApiService by lazy {
        val client = OkHttpClient.Builder().build()
        Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(client)
            .build()
            .create(SponsorBlockApiService::class.java)
    }

    fun getLastUpdateTime(): String {
        val timestamp = prefs.getLong("last_update", 0L)
        return if (timestamp == 0L) "Never" else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }
    }

    suspend fun updateScript(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = githubApiService.downloadLatestScript()
            if (response.isSuccessful) {
                response.body()?.byteStream()?.use { input ->
                    scriptFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to download script: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getScript(): String? {
        return if (scriptFile.exists()) {
            scriptFile.readText()
        } else {
            null
        }
    }

    fun getUserAgent(): String {
        return prefs.getString("custom_user_agent", "") ?: ""
    }

    fun setUserAgent(ua: String) {
        prefs.edit().putString("custom_user_agent", ua).apply()
    }

    suspend fun fetchSegments(videoID: String): List<Segment> = withContext(Dispatchers.IO) {
        try {
            val hash = sha256(videoID)
            val prefix = hash.substring(0, 4)
            val userID = prefs.getString("userID", "") ?: ""
            val categories = listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "music_offtopic", "poi_highlight")
            val actionTypes = listOf("skip", "poi")
            
            val response = apiService.getSkipSegments(prefix, categories, actionTypes, "YouTube", userID)
            
            if (response.isSuccessful) {
                val results = response.body() ?: emptyList()
                android.util.Log.d("SponsorBlockManager", "Fetched ${results.size} results for prefix $prefix")
                // Match the full videoID from the k-anonymity results
                val segments = results.find { it.videoID == videoID }?.segments ?: emptyList()
                android.util.Log.d("SponsorBlockManager", "Found ${segments.size} segments for videoID $videoID: $segments")
                segments
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SponsorBlockManager", "Failed to fetch segments: ${response.code()} - $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("SponsorBlockManager", "Error fetching segments", e)
            emptyList()
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun shouldSkip(category: String): Boolean {
        // Default to true for sponsor, selfpromo, etc.
        val default = when (category) {
            "sponsor", "selfpromo", "interaction", "intro", "outro" -> true
            else -> false
        }
        return prefs.getBoolean("skip_cat_$category", default)
    }

    fun setShouldSkip(category: String, skip: Boolean) {
        prefs.edit().putBoolean("skip_cat_$category", skip).apply()
    }
}

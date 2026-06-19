package com.eetu.youtubeapp.bridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.eetu.youtubeapp.data.YouTubeSettingsManager
import com.eetu.youtubeapp.data.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

class AndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val onSkipDetected: (String, Double) -> Unit = { _, _ -> },
    private val onHighlightDetected: (Double) -> Unit = {},
    private val onOpenSettings: () -> Unit = {},
    private val onOpenBrowserSettings: () -> Unit = {},
    private val onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onMetadataChanged: (String, String) -> Unit = { _, _ -> },
    private val onPlaybackStateChanged: (Boolean) -> Unit = {},
    private val onScrollChanged: (Int) -> Unit = {},
    private val onNavigationStateChanged: () -> Unit = {}
) {
    private val prefs = context.getSharedPreferences("sponsorblock_prefs", Context.MODE_PRIVATE)
    private val youtubeSettingsManager = YouTubeSettingsManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private var currentVideoId: String? = null
    @Volatile
    private var segments: List<Segment> = emptyList()
    private var lastSkippedUuid: String? = null
    private val shownHighlights = mutableSetOf<String>()

    @JavascriptInterface
    fun log(message: String) {
        Log.d("AndroidBridge", "JS Log: $message")
    }

    @JavascriptInterface
    fun getUserAgent(): String {
        return webView.settings.userAgentString
    }

    @JavascriptInterface
    fun postMessage(json: String) {
        Log.d("AndroidBridge", "JS Message: $json")
        try {
            val data = JSONObject(json)
            val type = data.getString("type")
            val msgId = data.optString("msgId")

            when (type) {
                "storage.local.get" -> {
                    val keys = data.get("keys")
                    val result = JSONObject()
                    if (keys is String) {
                        result.put(keys, prefs.getString(keys, null))
                    } else if (keys is org.json.JSONArray) {
                        for (i in 0 until keys.length()) {
                            val key = keys.getString(i)
                            result.put(key, prefs.getString(key, null))
                        }
                    }
                    sendResponse(msgId, result)
                }
                "storage.local.set" -> {
                    val items = data.getJSONObject("items")
                    val editor = prefs.edit()
                    val iterator = items.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        editor.putString(key, items.getString(key))
                    }
                    editor.apply()
                }
                "runtime.sendMessage" -> {
                    // Handle runtime messages if needed
                    Log.d("AndroidBridge", "Runtime Message: ${data.optJSONObject("payload")}")
                }
            }
        } catch (e: Exception) {
            Log.e("AndroidBridge", "Error parsing message", e)
        }
    }

    @JavascriptInterface
    fun openSettings() {
        Log.d("AndroidBridge", "openSettings called")
        webView.post {
            onOpenSettings()
        }
    }

    @JavascriptInterface
    fun openBrowserSettings() {
        Log.d("AndroidBridge", "openBrowserSettings called")
        webView.post {
            onOpenBrowserSettings()
        }
    }

    private var lastWidth = 0
    private var lastHeight = 0

    @JavascriptInterface
    fun onVideoDimensionsChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        
        // Trigger only on actual change to avoid log storms and unnecessary orientation requests
        if (width == lastWidth && height == lastHeight) {
            return
        }

        lastWidth = width
        lastHeight = height

        Log.d("AndroidBridge", "onVideoDimensionsChanged: ${width}x${height}")
        webView.post {
            onVideoDimensionsChanged(width, height)
        }
    }

    @JavascriptInterface
    fun onVideoIdChanged(videoId: String) {
        Log.d("AndroidBridge", "onVideoIdChanged: $videoId (current: $currentVideoId)")
        if (videoId == currentVideoId && segments.isNotEmpty()) {
            pushSegmentsToJs()
            return
        }
        currentVideoId = videoId
        segments = emptyList()
        lastSkippedUuid = null
        shownHighlights.clear()
        
        // Reset dimensions for the new video
        lastWidth = 0
        lastHeight = 0
        
        scope.launch {
            segments = youtubeSettingsManager.fetchSegments(videoId)
            Log.d("AndroidBridge", "Fetched ${segments.size} segments for $videoId")
            pushSegmentsToJs()
        }
    }

    private fun pushSegmentsToJs() {
        val currentSegments = segments
        val segmentsJson = org.json.JSONArray()
        // Only include segments the user wants to see/skip
        currentSegments.filter { youtubeSettingsManager.shouldSkip(it.category) || it.category == "poi_highlight" }.forEach { segment ->
            val obj = org.json.JSONObject()
            obj.put("start", segment.start)
            obj.put("end", segment.end)
            obj.put("category", segment.category)
            segmentsJson.put(obj)
        }
        webView.post {
            webView.evaluateJavascript("if(window.setSegments) { window.setSegments($segmentsJson); }", null)
        }
    }

    @JavascriptInterface
    fun onTimeUpdate(currentTime: Double) {
        val currentSegments = segments
        if (currentSegments.isEmpty()) return

        // Auto-skip logic
        val segmentToSkip = currentSegments.find { segment ->
            currentTime >= segment.start && currentTime < segment.end &&
                    youtubeSettingsManager.shouldSkip(segment.category) &&
                    segment.uuid != lastSkippedUuid
        }

        if (segmentToSkip != null) {
            Log.d("AndroidBridge", "Auto-skipping segment: ${segmentToSkip.category} (${segmentToSkip.start} - ${segmentToSkip.end})")
            lastSkippedUuid = segmentToSkip.uuid
            onSkipDetected(segmentToSkip.category, segmentToSkip.start)
            skipTo(segmentToSkip.end)
        }
        
        // Highlight logic
        currentSegments.filter { it.category == "poi_highlight" }.forEach { highlight ->
            if (!shownHighlights.contains(highlight.uuid)) {
                // Show if we are within 60 seconds of the highlight, or in the first 15 seconds of the video
                if (currentTime < highlight.start && (currentTime > highlight.start - 60 || currentTime < 15)) {
                    Log.d("AndroidBridge", "Highlight detected at ${highlight.start}, showing button")
                    shownHighlights.add(highlight.uuid)
                    webView.post {
                        onHighlightDetected(highlight.start)
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun jumpToTime(seconds: Double) {
        Log.d("AndroidBridge", "jumpToTime called with: $seconds")
        skipTo(seconds)
    }

    @JavascriptInterface
    fun updateMetadata(title: String, artist: String) {
        webView.post {
            onMetadataChanged(title, artist)
        }
    }

    @JavascriptInterface
    fun updatePlaybackState(isPlaying: Boolean) {
        webView.post {
            onPlaybackStateChanged(isPlaying)
        }
    }

    @JavascriptInterface
    fun onScroll(y: Int) {
        webView.post {
            onScrollChanged(y)
        }
    }

    @JavascriptInterface
    fun onNavigationStateChanged() {
        webView.post {
            onNavigationStateChanged()
        }
    }

    @JavascriptInterface
    fun share(url: String) {
        Log.d("AndroidBridge", "share called with: $url")
        webView.post {
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        }
    }

    private fun skipTo(seconds: Double) {
        webView.post {
            webView.evaluateJavascript("if(window._sb_player) { window._sb_player.currentTime = $seconds; }", null)
        }
    }

    private fun sendResponse(msgId: String, payload: Any) {
        val response = JSONObject().apply {
            put("type", "response")
            put("msgId", msgId)
            put("payload", payload)
        }
        val script = "window.onNativeMessage('${response.toString().replace("'", "\\'")}');"
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}

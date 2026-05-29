package com.eetu.youtubeapp.bridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.eetu.youtubeapp.data.SponsorBlockManager
import com.eetu.youtubeapp.data.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

class AndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val onSkipDetected: (String) -> Unit = {},
    private val onHighlightDetected: (Double) -> Unit = {},
    private val onOpenSettings: () -> Unit = {},
    private val onOpenBrowserSettings: () -> Unit = {},
    private val onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    private val prefs = context.getSharedPreferences("sponsorblock_prefs", Context.MODE_PRIVATE)
    private val sponsorBlockManager = SponsorBlockManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private var currentVideoId: String? = null
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
        Log.d("AndroidBridge", "onVideoIdChanged called with: $videoId")
        if (videoId == currentVideoId) {
            Log.d("AndroidBridge", "Video ID is the same, ignoring.")
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
            segments = sponsorBlockManager.fetchSegments(videoId)
            Log.d("AndroidBridge", "Fetched ${segments.size} segments for $videoId")
        }
    }

    @JavascriptInterface
    fun onTimeUpdate(currentTime: Double) {
        if (segments.isEmpty()) return

        // Auto-skip logic
        val segmentToSkip = segments.find { segment ->
            currentTime >= segment.start && currentTime < segment.end &&
                    sponsorBlockManager.shouldSkip(segment.category) &&
                    segment.uuid != lastSkippedUuid
        }

        if (segmentToSkip != null) {
            Log.d("AndroidBridge", "Auto-skipping segment: ${segmentToSkip.category} (${segmentToSkip.start} - ${segmentToSkip.end})")
            lastSkippedUuid = segmentToSkip.uuid
            onSkipDetected(segmentToSkip.category)
            skipTo(segmentToSkip.end)
        }
        
        // Highlight logic
        segments.filter { it.category == "poi_highlight" }.forEach { highlight ->
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

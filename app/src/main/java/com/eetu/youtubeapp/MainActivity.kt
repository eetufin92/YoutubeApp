package com.eetu.youtubeapp

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.app.PictureInPictureParams
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Rational
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eetu.youtubeapp.navigation.Destination
import com.eetu.youtubeapp.ui.components.YoutubeWebView
import com.eetu.youtubeapp.ui.settings.SettingsScreen
import com.eetu.youtubeapp.ui.settings.BrowserSettingsScreen
import com.eetu.youtubeapp.ui.theme.YoutubeAppTheme

class MainActivity : ComponentActivity() {
    private var isPlayerVisible = true
    private var isFullscreen = false
    private var isInPip = mutableStateOf(false)
    private var intentUrl = mutableStateOf<String?>(null)
    private var videoDimensions = Pair(0, 0) // Use plain Pair for sync access

    companion object {
        var isAppVisible = false
        var currentIsPlaying = false
        var currentTitle: String? = null
        var currentArtist: String? = null
        var currentUrl: String = ""
        val isWatchPage: Boolean
            get() = currentUrl.contains("/watch") || currentUrl.contains("/shorts")
    }

    override fun onStart() {
        super.onStart()
        isAppVisible = true
        com.eetu.youtubeapp.service.PlaybackService.stop(this)
    }

    override fun onStop() {
        super.onStop()
        isAppVisible = false
        if (currentIsPlaying && isWatchPage) {
            com.eetu.youtubeapp.service.PlaybackService.start(this, currentTitle, currentArtist)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { url ->
                intentUrl.value = url
            }
        }
    }

    override fun onUserLeaveHint() {
        android.util.Log.d("MainActivity", "onUserLeaveHint: isPlayerVisible=$isPlayerVisible, isFullscreen=$isFullscreen")
        if (isPlayerVisible && isFullscreen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            enterPipMode()
        }
        super.onUserLeaveHint()
    }

    private fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val (w, h) = videoDimensions
            android.util.Log.d("MainActivity", "enterPipMode: dimensions=${w}x${h}")
            val ratio = if (w > 0 && h > 0) {
                val r = w.toFloat() / h.toFloat()
                if (r < 1 / 2.39f) Rational(100, 239)
                else if (r > 2.39f) Rational(239, 100)
                else Rational(w, h)
            } else {
                Rational(16, 9)
            }

            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }

            android.util.Log.d("MainActivity", "enterPipMode: entering with ratio $ratio")
            val result = enterPictureInPictureMode(builder.build())
            android.util.Log.d("MainActivity", "enterPipMode: result=$result")
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        android.util.Log.d("MainActivity", "onPictureInPictureModeChanged: isInPip=$isInPictureInPictureMode")
        isInPip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            intentUrl.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            YoutubeAppTheme {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val context = LocalContext.current
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }
                    
                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val backStack = rememberNavBackStack(Destination.Player)

                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                ) { destination ->
                    when (destination) {
                        is Destination.Player -> {
                            isPlayerVisible = true
                            NavEntry(key = destination) {
                                PlayerScreen(
                                    externalUrl = intentUrl.value,
                                    onUrlHandled = { intentUrl.value = null },
                                    videoDimensions = videoDimensions,
                                    onVideoDimensionsChanged = { w, h ->
                                        videoDimensions = Pair(w, h)
                                    },
                                    onFullscreenChanged = { fullscreen -> 
                                        android.util.Log.d("MainActivity", "onFullscreenChanged: $fullscreen")
                                        isFullscreen = fullscreen 
                                    },
                                    isInPip = isInPip.value,
                                    onNavigateToSettings = {
                                        if (!backStack.contains(Destination.Settings)) {
                                            backStack.add(Destination.Settings)
                                        }
                                    },
                                    onNavigateToBrowserSettings = {
                                        if (!backStack.contains(Destination.BrowserSettings)) {
                                            backStack.add(Destination.BrowserSettings)
                                        }
                                    }
                                )
                            }
                        }
                        is Destination.Settings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                SettingsScreen(
                                    onNavigateBack = { 
                                        if (backStack.size > 1) {
                                            backStack.removeAt(backStack.size - 1)
                                        }
                                    }
                                )
                            }
                        }
                        is Destination.BrowserSettings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                BrowserSettingsScreen(
                                    onNavigateBack = { 
                                        if (backStack.size > 1) {
                                            backStack.removeAt(backStack.size - 1)
                                        }
                                    }
                                )
                            }
                        }
                        else -> NavEntry(destination) {}
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    externalUrl: String? = null,
    onUrlHandled: () -> Unit = {},
    videoDimensions: Pair<Int, Int> = Pair(0, 0),
    onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> },
    onFullscreenChanged: (Boolean) -> Unit = {},
    isInPip: Boolean = false,
    onNavigateToSettings: () -> Unit,
    onNavigateToBrowserSettings: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val youtubeSettingsManager = remember { com.eetu.youtubeapp.data.YouTubeSettingsManager(context) }
    val activity = remember(context) { context.findActivity() }
    var isFullscreen by remember { mutableStateOf(false) }
    
    var jumpToTimeRequest by remember { mutableStateOf<Double?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            snackbarHost = {}
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                val initialUrl = remember { externalUrl ?: "https://m.youtube.com" }
                YoutubeWebView(
                    modifier = Modifier.fillMaxSize(),
                    initialUrl = initialUrl,
                    onSkipDetected = { category, startTime ->
                        // The WebView now handles its own overlay for skips
                        android.util.Log.d("MainActivity", "Skipped $category starting at $startTime")
                    },
                    onHighlightDetected = { time ->
                        scope.launch {
                            val durationSeconds = youtubeSettingsManager.getNoticeDuration()
                            val snackbarJob = launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Highlight found",
                                    actionLabel = "Jump",
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    jumpToTimeRequest = time
                                }
                            }
                            delay(durationSeconds * 1000L)
                            snackbarJob.cancel()
                        }
                    },
                    onFullscreenStateChanged = { fullscreen ->
                        isFullscreen = fullscreen
                        onFullscreenChanged(fullscreen)
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val builder = PictureInPictureParams.Builder()
                            if (fullscreen) {
                                val (w, h) = videoDimensions
                                val ratio = if (w > 0 && h > 0) {
                                    val r = w.toFloat() / h.toFloat()
                                    if (r < 1 / 2.39f) Rational(100, 239)
                                    else if (r > 2.39f) Rational(239, 100)
                                    else Rational(w, h)
                                } else {
                                    Rational(16, 9)
                                }
                                builder.setAspectRatio(ratio)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    builder.setAutoEnterEnabled(true)
                                }
                            } else {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    builder.setAutoEnterEnabled(false)
                                }
                            }
                            activity?.setPictureInPictureParams(builder.build())
                        }

                        if (fullscreen) {
                            // based on the device sensor and user preference.
                            val newOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                            
                            if (activity?.requestedOrientation != newOrientation) {
                                activity?.requestedOrientation = newOrientation
                            }
                        } else {
                            if (activity?.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }

                        val window = activity?.window
                        if (window != null) {
                            val controller = WindowCompat.getInsetsController(window, window.decorView)
                            if (fullscreen) {
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            } else {
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                    },
                    onVideoDimensionsChanged = { w, h ->
                        onVideoDimensionsChanged(w, h)
                    },
                    onOpenSettings = onNavigateToSettings,
                    onOpenBrowserSettings = onNavigateToBrowserSettings,
                    jumpToTimeRequest = jumpToTimeRequest,
                    onJumpToTimeHandled = { jumpToTimeRequest = null },
                    loadUrlRequest = externalUrl,
                    onUrlLoaded = onUrlHandled
                )
            }
        }

        // Show SnackbarHost in a Popup when in fullscreen, or just as a TopCenter overlay otherwise
        val snackbarContent: @Composable () -> Unit = {
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(top = if (isFullscreen) 16.dp else 48.dp)
            ) { data ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            TextButton(onClick = { data.performAction() }) {
                                Text(label, color = Color.Cyan)
                            }
                        }
                    },
                    dismissAction = {
                        if (data.visuals.actionLabel != null) {
                            TextButton(onClick = { data.dismiss() }) {
                                Text("Close", color = Color.LightGray)
                            }
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
        }

        if (isFullscreen) {
            Popup(
                alignment = Alignment.TopCenter,
                properties = PopupProperties(focusable = false)
            ) {
                snackbarContent()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                snackbarContent()
            }
        }
    }
}

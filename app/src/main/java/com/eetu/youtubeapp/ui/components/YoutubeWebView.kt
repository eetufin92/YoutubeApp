package com.eetu.youtubeapp.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.eetu.youtubeapp.bridge.AndroidBridge
import com.eetu.youtubeapp.data.SponsorBlockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://m.youtube.com",
    onLoadingStateChanged: (Boolean) -> Unit = {},
    onSkipDetected: (String, Double) -> Unit = { _, _ -> },
    onHighlightDetected: (Double) -> Unit = {},
    onFullscreenStateChanged: (Boolean) -> Unit = {},
    onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenBrowserSettings: () -> Unit = {},
    jumpToTimeRequest: Double? = null,
    onJumpToTimeHandled: () -> Unit = {},
    loadUrlRequest: String? = null,
    onUrlLoaded: () -> Unit = {},
    isInPip: Boolean = false
) {
    val context = LocalContext.current
    val sponsorBlockManager = remember { SponsorBlockManager(context) }
    val isDark = isSystemInDarkTheme()
    val subtitleSize = sponsorBlockManager.getSubtitleSize()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initialUrl) }

    val isHomePage = remember(currentUrl) {
        val uri = android.net.Uri.parse(currentUrl)
        uri.host?.contains("youtube.com") == true && (uri.path == "/" || uri.path.isNullOrEmpty() || uri.path == "/index")
    }

    val mediaSession = remember {
        MediaSession(context, "YoutubeApp").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    webViewInstance?.evaluateJavascript("if(window._sb_player) { window._sb_player.play(); }", null)
                }
                override fun onPause() {
                    webViewInstance?.evaluateJavascript("if(window._sb_player) { window._sb_player.pause(); }", null)
                }
                override fun onSkipToNext() {
                    webViewInstance?.evaluateJavascript("document.querySelector('.ytp-next-button')?.click() || document.querySelector('.ytm-next-button')?.click()", null)
                }
                override fun onSkipToPrevious() {
                    webViewInstance?.evaluateJavascript("window.history.back()", null)
                }
            })
            isActive = true
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var customViewRef by remember { mutableStateOf<View?>(null) }
    var customViewCallbackRef by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var skipInfo by remember { mutableStateOf<Pair<String, Double>?>(null) }

    val currentOnSkipDetected by rememberUpdatedState(onSkipDetected)
    val currentOnHighlightDetected by rememberUpdatedState(onHighlightDetected)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnOpenBrowserSettings by rememberUpdatedState(onOpenBrowserSettings)
    val currentOnVideoDimensionsChanged by rememberUpdatedState(onVideoDimensionsChanged)

    val noticeDuration = remember { sponsorBlockManager.getNoticeDuration() }

    LaunchedEffect(skipInfo) {
        if (skipInfo != null) {
            delay(noticeDuration * 1000L)
            skipInfo = null
        }
    }

    // Handle Back Button for WebView or Fullscreen
    if (isFullscreen) {
        BackHandler {
            customViewCallbackRef?.onCustomViewHidden()
        }
    } else if (canGoBack) {
        BackHandler {
            webViewInstance?.goBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The main WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PersistentWebView(ctx).apply {
                    webViewInstance = this
                    setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = true
                        allowFileAccess = true
                        mediaPlaybackRequiresUserGesture = false
                        
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false

                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        // Enable modern dark mode support
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            @Suppress("DEPRECATION")
                            WebSettingsCompat.setForceDark(settings, if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
                        }

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                            @Suppress("DEPRECATION")
                            WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING)
                        }

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
                        }

                        val customUA = sponsorBlockManager.getUserAgent()
                        userAgentString = if (customUA.isNotEmpty()) {
                            customUA
                        } else {
                            settings.userAgentString.replace("; wv", "")
                        }
                    }

                    val bridge = AndroidBridge(ctx, this, { category, startTime ->
                        post {
                            skipInfo = category to startTime
                            currentOnSkipDetected(category, startTime)
                        }
                    }, { currentOnHighlightDetected(it) }, 
                    { currentOnOpenSettings() }, 
                    { currentOnOpenBrowserSettings() }, 
                    { w, h -> currentOnVideoDimensionsChanged(w, h) },
                    { title, artist ->
                        mediaSession.setMetadata(MediaMetadata.Builder()
                            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                            .build())
                    },
                    { isPlaying ->
                        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                        mediaSession.setPlaybackState(PlaybackState.Builder()
                            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .build())
                    })
                    addJavascriptInterface(bridge, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            onLoadingStateChanged(true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onLoadingStateChanged(false)
                            canGoBack = view?.canGoBack() ?: false
                            currentUrl = url ?: ""
                            if (url?.contains("youtube.com") == true) {
                                injectScripts(view, isDark, subtitleSize)
                                
                                CoroutineScope(Dispatchers.Main).launch {
                                    var lastUrl = url
                                    repeat(40) {
                                        delay(500)
                                        val currentUrlVal = view?.url
                                        if (currentUrlVal != null && currentUrlVal != lastUrl) {
                                            lastUrl = currentUrlVal
                                            currentUrl = currentUrlVal
                                            canGoBack = view.canGoBack()
                                            injectScripts(view, isDark, subtitleSize)
                                        }
                                    }
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val failingUrl = request.url?.toString()
                                view?.loadDataWithBaseURL(
                                    failingUrl,
                                    getErrorHtml(isDark, error?.description?.toString()),
                                    "text/html",
                                    "UTF-8",
                                    failingUrl
                                )
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            view?.loadDataWithBaseURL(
                                failingUrl,
                                getErrorHtml(isDark, description),
                                "text/html",
                                "UTF-8",
                                failingUrl
                            )
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (customViewRef != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            
                            val activity = ctx.findActivity()
                            val contentFrame = activity?.findViewById<FrameLayout>(android.R.id.content)
                            
                            view?.apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                                // Force layout params to match parent to avoid sizing issues during transition
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            
                            contentFrame?.addView(view)
                            view?.requestFocus()
                            
                            customViewRef = view
                            customViewCallbackRef = callback
                            isFullscreen = true
                            onFullscreenStateChanged(true)
                        }

                        override fun onHideCustomView() {
                            val activity = ctx.findActivity()
                            val contentFrame = activity?.findViewById<FrameLayout>(android.R.id.content)
                            
                            customViewRef?.let { view ->
                                contentFrame?.removeView(view)
                            }
                            
                            customViewRef = null
                            customViewCallbackRef?.onCustomViewHidden()
                            customViewCallbackRef = null
                            isFullscreen = false
                            onFullscreenStateChanged(false)
                        }
                    }

                    setOnLongClickListener {
                        val result = hitTestResult
                        val type = result.type
                        val extra = result.extra

                        if (extra != null && (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                           type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                            val handler = @SuppressLint("HandlerLeak") object : android.os.Handler(android.os.Looper.getMainLooper()) {
                                override fun handleMessage(msg: android.os.Message) {
                                    val url = msg.data.getString("url")
                                    val finalUrl = if (!url.isNullOrEmpty()) url else extra
                                    showLongPressDialog(ctx, this@apply, finalUrl)
                                }
                            }
                            val msg = handler.obtainMessage()
                            requestFocusNodeHref(msg)
                            true
                        } else if (extra != null && type == WebView.HitTestResult.IMAGE_TYPE) {
                            showLongPressDialog(ctx, this@apply, extra)
                            true
                        } else {
                            false
                        }
                    }

                    loadUrl(initialUrl)
                }
            },
            update = { view ->
                view.setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                if (jumpToTimeRequest != null) {
                    view.evaluateJavascript("if(window._sb_player) { window._sb_player.currentTime = $jumpToTimeRequest; }", null)
                    onJumpToTimeHandled()
                }
                if (loadUrlRequest != null) {
                    view.loadUrl(loadUrlRequest)
                    onUrlLoaded()
                }

                // Update subtitle size dynamically
                view.evaluateJavascript("""
                    (function() {
                        var styleId = 'sb-subtitle-style';
                        var style = document.getElementById(styleId);
                        if (!style) {
                            style = document.createElement('style');
                            style.id = styleId;
                            document.head.appendChild(style);
                        }
                        style.textContent = `
                            .caption-window, .ytp-caption-segment, .ytm-subtitle, .ytp-caption-container, .ytp-caption-segment span {
                                font-size: ${subtitleSize}% !important;
                                line-height: normal !important;
                            }
                            video::-webkit-media-text-track-display {
                                font-size: ${subtitleSize}% !important;
                            }
                        `;
                    })();
                """.trimIndent(), null)

                if (isInPip) {
                    view.evaluateJavascript("""
                        (function() {
                            const video = document.querySelector('video');
                            if (video && video.paused) video.play();
                            window._sb_pip_active = true;
                        })();
                    """.trimIndent(), null)
                } else {
                    view.evaluateJavascript("window._sb_pip_active = false;", null)
                }
            }
        )

        if (isHomePage && !isFullscreen) {
            var showMenu by remember { mutableStateOf(false) }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 72.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showMenu = true },
                    containerColor = Color.Red,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("SponsorBlock Settings") },
                        leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Browser Settings") },
                        leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenBrowserSettings()
                        }
                    )
                }
            }
        }

        val skipOverlay: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = skipInfo != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .padding(top = if (isFullscreen) 100.dp else 120.dp)
            ) {
                skipInfo?.let { (category, startTime) ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Skipped $category",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            TextButton(
                                onClick = {
                                    webViewInstance?.evaluateJavascript(
                                        "if(window._sb_player) { window._sb_player.currentTime = $startTime; }",
                                        null
                                    )
                                    skipInfo = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF3EA6FF))
                            ) {
                                Text("Undo", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (isFullscreen) {
            Popup(
                alignment = Alignment.TopCenter,
                properties = PopupProperties(focusable = false)
            ) {
                skipOverlay()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                skipOverlay()
            }
        }
    }
}

private fun injectScripts(webView: WebView?, isDark: Boolean, subtitleSize: Int) {
    val view = webView ?: return

    val cosmeticScript = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
            
            var styleId = 'sb-cosmetic-style';
            var style = document.getElementById(styleId);
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
            }
            style.textContent = `
                .mobile-topbar-header-content.ytd-app-promo,
                .ytd-app-promo,
                ytm-pwa-install-banner,
                .ytm-app-update-banner,
                #app-install-banner,
                .upsell-dialog-renderer,
                .modern-sharing-item-install-app,
                .ytm-pwa-install-banner-header,
                .ytm-app-install-banner,
                ytm-promoted-sparkles-web-renderer,
                ytm-mealbar-promo-renderer,
                ytm-brand-teaser-renderer,
                ytm-app-promo-renderer,
                .ytm-app-promo-renderer,
                ytm-item-section-renderer[section-identifier="app-promo"],
                .topbar-header-content .yt-spec-button-shape-next--call-to-action,
                .ytm-topbar-header-container .yt-spec-button-shape-next--call-to-action,
                #player-ads,
                .ytm-promoted-video-renderer,
                .ytm-player-overlay-renderer .yt-spec-button-shape-next--call-to-action,
                .ytm-player-overlay-renderer .ytm-app-promo-renderer,
                .ytm-app-install-banner,
                [aria-label*="Open App"],
                [aria-label*="open app"],
                .yt-spec-button-shape-next--call-to-action[aria-label*="App"],
                ytm-companion-ad-renderer,
                .ytp-overflow-button,
                .ytp-button[aria-label*="App"],
                a[href^="intent://"],
                button[onclick*="intent://"] {
                    display: none !important;
                }
            `;

            var subStyleId = 'sb-subtitle-style';
            var subStyle = document.getElementById(subStyleId);
            if (!subStyle) {
                subStyle = document.createElement('style');
                subStyle.id = subStyleId;
                document.head.appendChild(subStyle);
            }
            subStyle.textContent = `
                .caption-window, .ytp-caption-segment, .ytm-subtitle, .ytp-caption-container, .ytp-caption-segment span {
                    font-size: ${subtitleSize}% !important;
                    line-height: normal !important;
                }
                video::-webkit-media-text-track-display {
                    font-size: ${subtitleSize}% !important;
                }
            `;
            
            if (${isDark}) {
                document.documentElement.setAttribute('dark', 'true');
            } else {
                document.documentElement.removeAttribute('dark');
            }
        })();
    """.trimIndent()
    view.evaluateJavascript(cosmeticScript, null)

    val observerScript = """
        (function() {
            if (window._sb_observer_active) return;
            window._sb_observer_active = true;
            
            function interceptShare() {
                document.addEventListener('click', (e) => {
                    const shareButton = e.target.closest('button[aria-label*="Share"], button[aria-label*="Jaa"], .ytm-share-button, .share-panel-service-button, .yt-spec-button-shape-next[aria-label*="Share"], .yt-spec-button-shape-next[aria-label*="Jaa"]');
                    if (shareButton) {
                        e.preventDefault();
                        e.stopPropagation();
                        AndroidBridge.share(window.location.href);
                    }
                }, true);
            }
            interceptShare();

            function interceptShare() {
                document.addEventListener('click', (e) => {
                    const shareButton = e.target.closest('button[aria-label*="Share"], button[aria-label*="Jaa"], .ytm-share-button, .share-panel-service-button, .yt-spec-button-shape-next[aria-label*="Share"], .yt-spec-button-shape-next[aria-label*="Jaa"]');
                    if (shareButton) {
                        e.preventDefault();
                        e.stopPropagation();
                        AndroidBridge.share(window.location.href);
                    }
                }, true);
            }
            interceptShare();

            function removeIntentLinks() {
                document.querySelectorAll('a[href^="intent://"], [onclick*="intent://"]').forEach(el => {
                    el.style.display = 'none';
                    el.remove();
                });
            }
            setInterval(removeIntentLinks, 2000);
            removeIntentLinks();

            // Prevent YouTube from auto-pausing when losing focus/visibility or backgrounded
            const blockVisibility = (e) => {
                e.stopImmediatePropagation();
            };
            document.addEventListener('visibilitychange', blockVisibility, true);
            document.addEventListener('webkitvisibilitychange', blockVisibility, true);
            document.addEventListener('mozvisibilitychange', blockVisibility, true);
            document.addEventListener('msvisibilitychange', blockVisibility, true);
            document.addEventListener('pagehide', blockVisibility, true);
            
            const stateProp = { get: function() { return 'visible'; }, configurable: true };
            const hiddenProp = { get: function() { return false; }, configurable: true };
            
            Object.defineProperty(document, 'visibilityState', stateProp);
            Object.defineProperty(document, 'webkitVisibilityState', stateProp);
            Object.defineProperty(document, 'mozVisibilityState', stateProp);
            Object.defineProperty(document, 'msVisibilityState', stateProp);
            
            Object.defineProperty(document, 'hidden', hiddenProp);
            Object.defineProperty(document, 'webkitHidden', hiddenProp);
            Object.defineProperty(document, 'mozHidden', hiddenProp);
            Object.defineProperty(document, 'msHidden', hiddenProp);

            // Also prevent pausing on window blur and spoof focus
            window.addEventListener('blur', blockVisibility, true);
            document.hasFocus = function() { return true; };
            
            // IntersectionObserver spoofing to keep video "visible"
            const NativeObserver = window.IntersectionObserver;
            window.IntersectionObserver = class extends NativeObserver {
                constructor(callback, options) {
                    super((entries, observer) => {
                        const modifiedEntries = entries.map(entry => {
                            return new Proxy(entry, {
                                get: (target, prop) => {
                                    if (prop === 'isIntersecting') return true;
                                    if (prop === 'intersectionRatio') return 1;
                                    return target[prop];
                                }
                            });
                        });
                        callback(modifiedEntries, observer);
                    }, options);
                }
            };
            
            function clearElement(el) {
                while (el.firstChild) {
                    el.removeChild(el.firstChild);
                }
            }

            function injectSponsorBlockStyles() {
                const styleId = 'sb-colors-style';
                if (document.getElementById(styleId)) return;
                const style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    :root {
                        --sb-category-sponsor: #00d400;
                        --sb-category-selfpromo: #ffff00;
                        --sb-category-interaction: #cc00ff;
                        --sb-category-intro: #00ffff;
                        --sb-category-outro: #0202ed;
                        --sb-category-preview: #008fd6;
                        --sb-category-music_offtopic: #ff9900;
                        --sb-category-poi_highlight: #ff16b0;
                    }
                    .sb-segment {
                        position: absolute;
                        height: 100%;
                        opacity: 0.9;
                        pointer-events: none;
                        top: 0;
                    }
                `;
                document.head.appendChild(style);
            }

            window.setSegments = function(segments) {
                window._sb_segments = segments;
                renderSegments();
            };

            function renderSegments() {
                try {
                    const segments = window._sb_segments;

                    const video = document.querySelector('video');
                    if (!video || !video.duration || isNaN(video.duration)) return;

                   const progressBar = document.querySelector('yt-chaptered-progress-bar-line') ||
                                       document.querySelector('yt-progress-bar-line') ||
                                       document.querySelector('.ytProgressBarLineHost') ||
                                       document.querySelector('.ytm-progress-bar-line') ||
                                       document.querySelector('ytm-progress-bar');
                    
                    if (!progressBar) {
                        console.log("No progressbar for segments found");
                        return;
                    }

                    let previewBar = progressBar.querySelector('#previewbar');
                    if (!previewBar) {
                        previewBar = document.createElement('ul');
                        previewBar.id = 'previewbar';
                        previewBar.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:2147483647;list-style:none;padding:0;margin:0;';
                        progressBar.appendChild(previewBar);
                    }

                    const renderKey = (segments ? segments.length : 0) + '_' + (video.duration ? Math.floor(video.duration) : 0);
                    if (previewBar.dataset.renderKey === renderKey) return;
                    previewBar.dataset.renderKey = renderKey;

                    clearElement(previewBar);
                    if (!segments || segments.length === 0) {
                        console.log("segments not found");
                        return;
                    }

                    segments.forEach(function(seg) {
                        console.log("segment", JSON.stringify(seg));


                        var start = (seg.start / video.duration) * 100;
                        var end = (seg.end / video.duration) * 100;
                        
                        // Highlights are often points, ensure they are visible (at least 1.5% of the bar)
                        if (seg.category === 'poi_highlight' && (end - start) < 1.5) {
                            console.log("Adjusting poi_highlight duration");
                            end = start + 1.5;
                        }
                        
                        var right = 100 - end;
                        if (start > 100) return;

                        var li = document.createElement('li');
                        li.className = 'previewbar sb-segment';
                        li.setAttribute('sponsorblock-category', seg.category);
                        li.style.position = 'absolute';
                        li.style.height = '100%';
                        li.style.left = start + '%';
                        li.style.right = right + '%';
                        li.style.backgroundColor = 'var(--sb-category-' + seg.category + ', #888)';
                        li.textContent = '\u00A0'; 
                        previewBar.appendChild(li);
                    });
                } catch (e) {
                    console.error('SponsorBlock render error:', e);
                }
            }

            function getInfo() {
                const video = document.querySelector('video');
                const urlParams = new URLSearchParams(window.location.search);
                const videoId = urlParams.get('v');
                
                if (video && !video._sb_listener_added) {
                    video._sb_listener_added = true;
                    const reportDimensions = () => {
                        if (video.videoWidth > 0 && video.videoHeight > 0) {
                            AndroidBridge.onVideoDimensionsChanged(video.videoWidth, video.videoHeight);
                        }
                    };
                    video.addEventListener('loadedmetadata', reportDimensions);
                    video.addEventListener('resize', reportDimensions);
                    reportDimensions();
                }

                // Detect if an ad is currently playing
                const isAd = !!(
                    document.querySelector('.ad-showing') || 
                    document.querySelector('.ad-interrupting') ||
                    document.querySelector('.ytp-ad-player-overlay') ||
                    document.querySelector('.ytm-ad-playability-overlay-renderer')
                );
                
                // Extract metadata
                let title = document.title;
                if (title.endsWith(' - YouTube')) title = title.substring(0, title.length - 10);
                
                const channelName = document.querySelector('.ytm-item-section-renderer-header .yt-core-attributed-string')
                                   || document.querySelector('.ytm-channel-name')
                                   || document.querySelector('.slim-owner-channel-name')
                                   || 'YouTube';

                return { video, videoId, isAd, title, channelName: channelName.textContent || channelName };
            }
            
            let lastVideoId = null;
            let lastTitle = null;
            let lastIsPlaying = null;
            
            setInterval(() => {
                try {
                    const { video, videoId, isAd, title, channelName } = getInfo();
                    window._sb_player = video;
                    
                    // Fetch segments as soon as we have a videoId, even if an ad is playing.
                    // This allows pre-fetching while the ad is showing.
                    if (videoId && videoId !== lastVideoId) {
                        lastVideoId = videoId;
                        AndroidBridge.onVideoIdChanged(videoId);
                    }

                    if (title !== lastTitle) {
                        lastTitle = title;
                        AndroidBridge.updateMetadata(title, channelName);
                    }
                    
                    if (video) {
                        const isPlaying = !video.paused;
                        if (isPlaying !== lastIsPlaying) {
                            lastIsPlaying = isPlaying;
                            AndroidBridge.updatePlaybackState(isPlaying);
                        }
                    }
                    
                    // Only process time updates and rendering if not in an ad.
                    if (video && !video.paused && !isAd) {
                        AndroidBridge.onTimeUpdate(video.currentTime);
                    }

                    if (!isAd) {
                        injectSponsorBlockStyles();
                        renderSegments();
                    } else {
                        // Clear segments if an ad is showing to avoid rendering them on the ad's progress bar.
                        const progressBar = document.querySelector('yt-chaptered-progress-bar-line') ||
                                            document.querySelector('.ytm-progress-bar-line') ||
                                            document.querySelector('ytm-progress-bar');
                        if (progressBar) {
                            const previewBar = progressBar.querySelector('#previewbar');
                            if (previewBar) {
                                previewBar.innerHTML = '';
                                delete previewBar.dataset.renderKey;
                            }
                        }
                    }
                } catch (e) {
                    console.error('SponsorBlock interval error:', e);
                }
            }, 250); // Increased frequency to 250ms for snappier skipping and loading.
            window._sb_debug = true;
        })();
    """.trimIndent()

    view.evaluateJavascript(observerScript, null)
}

private fun showLongPressDialog(context: Context, webView: WebView, url: String) {
    AlertDialog.Builder(context)
        .setTitle(url)
        .setItems(arrayOf("Open", "Share", "Cancel")) { dialog, which ->
            when (which) {
                0 -> webView.loadUrl(url)
                1 -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
                2 -> dialog.dismiss()
            }
        }
        .show()
}

private fun getErrorHtml(isDark: Boolean, description: String?): String {
    val bgColor = if (isDark) "#0f0f0f" else "#ffffff"
    val textColor = if (isDark) "#ffffff" else "#000000"
    val secondaryColor = if (isDark) "#aaaaaa" else "#606060"
    val buttonBg = if (isDark) "#3ea6ff" else "#065fd4"
    val buttonText = if (isDark) "#000000" else "#ffffff"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body {
                    background-color: $bgColor;
                    color: $textColor;
                    font-family: "Roboto", "Arial", sans-serif;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    height: 100vh;
                    margin: 0;
                    padding: 24px;
                    text-align: center;
                    box-sizing: border-box;
                }
                .error-icon {
                    width: 120px;
                    height: 120px;
                    margin-bottom: 24px;
                    opacity: 0.8;
                }
                h1 {
                    font-size: 18px;
                    font-weight: 500;
                    margin: 0 0 12px 0;
                }
                p {
                    font-size: 14px;
                    color: $secondaryColor;
                    line-height: 20px;
                    margin: 0 0 32px 0;
                    max-width: 280px;
                }
                button {
                    background-color: $buttonBg;
                    color: $buttonText;
                    border: none;
                    padding: 0 16px;
                    height: 36px;
                    border-radius: 18px;
                    font-weight: 500;
                    font-size: 14px;
                    cursor: pointer;
                    text-transform: uppercase;
                }
                button:disabled {
                    opacity: 0.5;
                    cursor: default;
                }
            </style>
        </head>
        <body>
            <svg class="error-icon" viewBox="0 0 24 24" fill="$secondaryColor">
                <path d="M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10,10-4.48,10-10S17.52,2,12,2z M13,17h-2v-2h2V17z M13,13h-2V7h2V13z"/>
            </svg>
            <h1>Webpage not available</h1>
            <p>${description ?: "Check your network connection and try again."}</p>
            <button id="retryBtn" onclick="retry()">Retry</button>
            <script>
                function retry() {
                    const btn = document.getElementById('retryBtn');
                    btn.innerText = 'Retrying...';
                    btn.disabled = true;
                    // Small delay to show the state change before reloading
                    setTimeout(() => {
                        location.reload();
                    }, 500);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@SuppressLint("ViewConstructor")
private class PersistentWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        // Always report as visible to keep the engine running in background
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }
}

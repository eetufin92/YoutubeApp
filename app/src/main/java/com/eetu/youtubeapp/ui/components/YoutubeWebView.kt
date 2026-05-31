package com.eetu.youtubeapp.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
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
import androidx.compose.material3.ButtonDefaults
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
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    var customViewRef by remember { mutableStateOf<View?>(null) }
    var customViewCallbackRef by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var skipInfo by remember { mutableStateOf<Pair<String, Double>?>(null) }

    val currentOnSkipDetected by rememberUpdatedState(onSkipDetected)
    val currentOnHighlightDetected by rememberUpdatedState(onHighlightDetected)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnOpenBrowserSettings by rememberUpdatedState(onOpenBrowserSettings)
    val currentOnVideoDimensionsChanged by rememberUpdatedState(onVideoDimensionsChanged)

    LaunchedEffect(skipInfo) {
        if (skipInfo != null) {
            delay(5000)
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
                WebView(ctx).apply {
                    webViewInstance = this
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
                    { w, h -> currentOnVideoDimensionsChanged(w, h) })
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
                            if (url?.contains("youtube.com") == true) {
                                injectScripts(view, isDark)
                                
                                CoroutineScope(Dispatchers.Main).launch {
                                    var lastUrl = url
                                    repeat(40) {
                                        delay(500)
                                        val currentUrl = view?.url
                                        if (currentUrl != null && currentUrl != lastUrl) {
                                            lastUrl = currentUrl
                                            canGoBack = view.canGoBack()
                                            injectScripts(view, isDark)
                                        }
                                    }
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            return true
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
                if (jumpToTimeRequest != null) {
                    view.evaluateJavascript("if(window._sb_player) { window._sb_player.currentTime = $jumpToTimeRequest; }", null)
                    onJumpToTimeHandled()
                }
                if (loadUrlRequest != null) {
                    view.loadUrl(loadUrlRequest)
                    onUrlLoaded()
                }
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

        AnimatedVisibility(
            visible = skipInfo != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
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
}

private fun injectScripts(webView: WebView?, isDark: Boolean) {
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
            
            function injectSettingsButton() {
                if (!window.location.pathname.includes('/select_site') && !window.location.pathname.includes('/settings')) return;
                if (document.getElementById('sb-settings-button')) return;

                const target = document.querySelector('ytm-settings') || document.querySelector('.ytm-settings');
                if (!target) return;

                const sbButton = document.createElement('div');
                sbButton.id = 'sb-settings-button';
                sbButton.className = 'ytm-setting-single-option-menu-renderer';
                sbButton.style.padding = '16px';
                sbButton.style.borderBottom = '1px solid rgba(255,255,255,0.1)';
                
                const sbContainer = document.createElement('div');
                sbContainer.role = 'button';
                sbContainer.tabIndex = 0;
                sbContainer.className = 'setting-title-subtitle-block cairo-settings';
                
                const sbTitle = document.createElement('h3');
                sbTitle.className = 'setting-title';
                sbTitle.style.margin = '0';
                sbTitle.style.fontSize = '16px';
                const sbTitleSpan = document.createElement('span');
                sbTitleSpan.className = 'ytAttributedStringHost';
                sbTitleSpan.textContent = 'SponsorBlock Settings';
                sbTitle.appendChild(sbTitleSpan);
                
                const sbSubtitle = document.createElement('span');
                sbSubtitle.style.fontSize = '12px';
                sbSubtitle.style.color = '#aaa';
                const sbSubtitleSpan = document.createElement('span');
                sbSubtitleSpan.className = 'ytAttributedStringHost';
                sbSubtitleSpan.textContent = 'Configure skip categories';
                sbSubtitle.appendChild(sbSubtitleSpan);
                
                sbContainer.appendChild(sbTitle);
                sbContainer.appendChild(sbSubtitle);
                sbButton.appendChild(sbContainer);
                
                sbButton.onclick = (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    AndroidBridge.openSettings();
                };

                const browserButton = document.createElement('div');
                browserButton.id = 'browser-settings-button';
                browserButton.className = 'ytm-setting-single-option-menu-renderer';
                browserButton.style.padding = '16px';
                browserButton.style.borderBottom = '1px solid rgba(255,255,255,0.1)';
                
                const brContainer = document.createElement('div');
                brContainer.role = 'button';
                brContainer.tabIndex = 0;
                brContainer.className = 'setting-title-subtitle-block cairo-settings';
                
                const brTitle = document.createElement('h3');
                brTitle.className = 'setting-title';
                brTitle.style.margin = '0';
                brTitle.style.fontSize = '16px';
                const brTitleSpan = document.createElement('span');
                brTitleSpan.className = 'ytAttributedStringHost';
                brTitleSpan.textContent = 'Browser Settings';
                brTitle.appendChild(brTitleSpan);
                
                const brSubtitle = document.createElement('span');
                brSubtitle.style.fontSize = '12px';
                brSubtitle.style.color = '#aaa';
                const brSubtitleSpan = document.createElement('span');
                brSubtitleSpan.className = 'ytAttributedStringHost';
                brSubtitleSpan.textContent = 'Configure user agent';
                brSubtitle.appendChild(brSubtitleSpan);
                
                brContainer.appendChild(brTitle);
                brContainer.appendChild(brSubtitle);
                browserButton.appendChild(brContainer);
                
                browserButton.onclick = (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    AndroidBridge.openBrowserSettings();
                };

                const sections = target.querySelectorAll('ytm-setting-category-collection-renderer');
                if (sections.length > 0) {
                    sections[0].appendChild(sbButton);
                    sections[0].appendChild(browserButton);
                } else {
                    target.appendChild(sbButton);
                    target.appendChild(browserButton);
                }
            }

            const menuObserver = new MutationObserver(injectSettingsButton);
            menuObserver.observe(document.body, { childList: true, subtree: true });
            injectSettingsButton();

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

            // Prevent YouTube from auto-pausing when losing focus/visibility
            document.addEventListener('visibilitychange', (e) => {
                if (document.visibilityState === 'hidden' && window._sb_pip_active) {
                    e.stopImmediatePropagation();
                }
            }, true);
            
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
                                        document.querySelector('.ytm-progress-bar-line') ||
                                        document.querySelector('ytm-progress-bar');
                    
                    if (!progressBar) return;

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
                    if (!segments || segments.length === 0) return;

                    segments.forEach(function(seg) {
                        var start = (seg.start / video.duration) * 100;
                        var end = (seg.end / video.duration) * 100;
                        
                        // Highlights are often points, ensure they are visible (at least 1.5% of the bar)
                        if (seg.category === 'poi_highlight' && (end - start) < 1.5) {
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
                
                return { video, videoId, isAd };
            }
            
            let lastVideoId = null;
            
            setInterval(() => {
                try {
                    const { video, videoId, isAd } = getInfo();
                    window._sb_player = video;
                    
                    // Fetch segments as soon as we have a videoId, even if an ad is playing.
                    // This allows pre-fetching while the ad is showing.
                    if (videoId && videoId !== lastVideoId) {
                        lastVideoId = videoId;
                        AndroidBridge.onVideoIdChanged(videoId);
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

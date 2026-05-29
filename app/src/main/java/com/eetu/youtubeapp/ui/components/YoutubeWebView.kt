package com.eetu.youtubeapp.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    onSkipDetected: (String) -> Unit = {},
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

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Handle Back Button for WebView
    if (customView == null && canGoBack) {
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

                    val bridge = AndroidBridge(ctx, this, onSkipDetected, onHighlightDetected, onOpenSettings, onOpenBrowserSettings, onVideoDimensionsChanged)
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
                            if (customView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            
                            view?.apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                                // Force layout params to match parent to avoid sizing issues during transition
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            
                            customView = view
                            customViewCallback = callback
                            onFullscreenStateChanged(true)
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback = null
                            onFullscreenStateChanged(false)
                        }
                    }

                    setOnLongClickListener {
                        val result = hitTestResult
                        val url = result.extra
                        if (url != null && (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                           result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                            AlertDialog.Builder(ctx)
                                .setTitle(url)
                                .setItems(arrayOf("Open", "Share", "Cancel")) { dialog, which ->
                                    when (which) {
                                        0 -> loadUrl(url)
                                        1 -> {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, url)
                                                type = "text/plain"
                                            }
                                            val shareIntent = Intent.createChooser(sendIntent, null)
                                            ctx.startActivity(shareIntent)
                                        }
                                        2 -> dialog.dismiss()
                                    }
                                }
                                .show()
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

        if (customView != null) {
            BackHandler {
                customViewCallback?.onCustomViewHidden()
            }
            
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        customView!!
                    }
                )
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
                const { video, videoId, isAd } = getInfo();
                window._sb_player = video;
                
                if (videoId && videoId !== lastVideoId && !isAd) {
                    lastVideoId = videoId;
                    AndroidBridge.onVideoIdChanged(videoId);
                }
                
                if (video && !video.paused && !isAd) {
                    if (window._sb_debug) console.log('SponsorBlock: timeUpdate ' + video.currentTime);
                    AndroidBridge.onTimeUpdate(video.currentTime);
                }
            }, 1000);
            window._sb_debug = true;
        })();
    """.trimIndent()

    view.evaluateJavascript(observerScript, null)
}

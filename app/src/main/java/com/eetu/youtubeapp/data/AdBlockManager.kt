package com.eetu.youtubeapp.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FilterListSubscription(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isRemovable: Boolean = true,
    val lastUpdate: Long = 0L,
    val ruleCount: Int = 0
)

data class AdBlockFilters(
    val version: Int = 1,
    val blockedDomains: List<String> = emptyList(),
    val blockedUrlKeywords: List<String> = emptyList(),
    val cosmeticCssSelectors: List<String> = emptyList(),
    val prunedKeys: List<String> = emptyList()
)

class AdBlockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("adblock_prefs", Context.MODE_PRIVATE)
    private val listsDir = File(context.filesDir, "adblock_lists").apply { mkdirs() }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val filtersAdapter = moshi.adapter(AdBlockFilters::class.java)
    private val subscriptionListAdapter = moshi.adapter<List<FilterListSubscription>>(
        Types.newParameterizedType(List::class.java, FilterListSubscription::class.java)
    )

    companion object {
        @Volatile
        private var instance: AdBlockManager? = null

        fun getInstance(context: Context): AdBlockManager {
            return instance ?: synchronized(this) {
                instance ?: AdBlockManager(context.applicationContext).also { instance = it }
            }
        }

        // In-memory compiled filters for fast lookups across all references
        @Volatile
        private var domainSet: Set<String> = emptySet()
        @Volatile
        private var keywordList: List<String> = emptyList()
        @Volatile
        private var cachedCss: String = ""
        @Volatile
        private var cachedScriptlet: String = ""

        val PROTECTED_DOMAINS = setOf(
            "youtube.com",
            "m.youtube.com",
            "www.youtube.com",
            "googlevideo.com",
            "ytimg.com",
            "google.com",
            "googleapis.com",
            "gstatic.com",
            "ggpht.com"
        )

        fun isProtectedDomain(host: String): Boolean {
            val h = host.lowercase().trim()
            return PROTECTED_DOMAINS.any { h == it || h.endsWith(".$it") }
        }

        val DEFAULT_SUBSCRIPTIONS = listOf(
            FilterListSubscription(
                id = "ublock_filters",
                name = "uBlock filters",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_badware",
                name = "uBlock filters – Badware risks",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_privacy",
                name = "uBlock filters – Privacy",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_quick_fixes",
                name = "uBlock filters – Quick fixes",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_unbreak",
                name = "uBlock filters – Unbreak",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "easylist",
                name = "EasyList",
                url = "https://easylist.to/easylist/easylist.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "easyprivacy",
                name = "EasyPrivacy",
                url = "https://easylist.to/easylist/easyprivacy.txt",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "peter_lowe",
                name = "Peter Lowe’s Ad server list",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                isEnabled = false,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "youtube_curated",
                name = "YouTube App Curated Rules",
                url = "https://raw.githubusercontent.com/eetufin92/YoutubeApp/main/filters/adblock_filters.json",
                isEnabled = false,
                isRemovable = false
            )
        )

        val DEFAULT_BLOCKED_DOMAINS = listOf(
            "googleads.g.doubleclick.net",
            "ad.doubleclick.net",
            "pubads.g.doubleclick.net",
            "securepubads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "pagead2.googleadservices.com",
            "adservice.google.com",
            "afs.googlesyndication.com",
            "stats.g.doubleclick.net"
        )

        val DEFAULT_BLOCKED_URL_KEYWORDS = listOf(
            "/pagead/",
            "/api/stats/ads",
            "/get_midroll_info",
            "/ptracking",
            "adformat="
        )

        val DEFAULT_COSMETIC_SELECTORS = listOf(
            "ytd-ad-slot-renderer",
            "ytm-promoted-sparkles-web-renderer",
            "ytm-promoted-video-renderer",
            "ytm-companion-ad-renderer",
            "ytm-brand-teaser-renderer",
            "ytm-app-promo-renderer",
            ".ytm-app-promo-renderer",
            "ytm-item-section-renderer[section-identifier=\"app-promo\"]",
            "#player-ads",
            ".ad-showing",
            ".ad-interrupting",
            ".ytp-ad-overlay-container",
            ".ytp-ad-message-container",
            ".ytp-ad-action-interstitial",
            ".ytp-ad-player-overlay",
            ".ytp-ad-player-overlay-layout",
            ".ytp-ad-player-overlay-skip-or-preview",
            ".ytp-ad-skip-ad-slot",
            ".ytp-ad-skip-button-slot",
            "ytm-ad-playability-overlay-renderer",
            "ytd-in-feed-ad-layout-renderer",
            "ytd-player-legacy-desktop-watch-ads-renderer",
            ".video-ads.ytp-ad-module",
            ".ytp-ad-module"
        )

        val DEFAULT_PRUNED_KEYS = listOf(
            "adPlacements",
            "adSlots",
            "playerAds",
            "adBreakHeartbeatParams",
            "adPlacementRenderer"
        )
    }

    init {
        val schemaVersion = prefs.getInt("adblock_schema_version", 0)
        if (schemaVersion < 2) {
            // Upgrade to schema 2: reset subscriptions so all default to disabled and built-in filters active
            prefs.edit()
                .putInt("adblock_schema_version", 2)
                .putBoolean("use_builtin_filters", true)
                .remove("subscriptions_json")
                .apply()
            try {
                listsDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                android.util.Log.e("AdBlockManager", "Failed to clean old lists", e)
            }
        }
        compileActiveFilters()
    }

    fun isAdBlockEnabled(): Boolean {
        return prefs.getBoolean("adblock_enabled", true)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("adblock_enabled", enabled).apply()
    }

    fun useBuiltInFilters(): Boolean {
        return prefs.getBoolean("use_builtin_filters", true)
    }

    fun setUseBuiltInFilters(useBuiltIn: Boolean) {
        prefs.edit().putBoolean("use_builtin_filters", useBuiltIn).apply()
        compileActiveFilters()
    }

    fun isAutoUpdateOnLaunch(): Boolean {
        return prefs.getBoolean("auto_update_on_launch", true)
    }

    fun setAutoUpdateOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean("auto_update_on_launch", enabled).apply()
    }

    fun getLastUpdateTime(): String {
        val timestamp = prefs.getLong("last_filter_update", 0L)
        return if (timestamp == 0L) "Never (using defaults)" else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun getSubscriptions(): List<FilterListSubscription> {
        val json = prefs.getString("subscriptions_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val list = subscriptionListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) return list
            } catch (e: Exception) {
                android.util.Log.e("AdBlockManager", "Failed to parse subscriptions", e)
            }
        }
        return DEFAULT_SUBSCRIPTIONS
    }

    fun saveSubscriptions(subs: List<FilterListSubscription>) {
        val json = subscriptionListAdapter.toJson(subs)
        prefs.edit().putString("subscriptions_json", json).apply()
        compileActiveFilters()
    }

    fun toggleSubscription(id: String, enabled: Boolean) {
        val updated = getSubscriptions().map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveSubscriptions(updated)
    }

    fun editSubscription(id: String, newName: String, newUrl: String) {
        val updated = getSubscriptions().map { sub ->
            if (sub.id == id) {
                val cleanUrl = newUrl.trim()
                val cleanName = newName.trim().ifBlank { sub.name }
                val urlChanged = sub.url != cleanUrl
                if (urlChanged) {
                    val file = File(listsDir, "${sub.id}.txt")
                    if (file.exists()) file.delete()
                    sub.copy(name = cleanName, url = cleanUrl, lastUpdate = 0L, ruleCount = 0)
                } else {
                    sub.copy(name = cleanName)
                }
            } else sub
        }
        saveSubscriptions(updated)
    }

    fun addCustomSubscription(name: String, url: String): FilterListSubscription {
        val sub = FilterListSubscription(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { "Custom Filter List" },
            url = url.trim(),
            isEnabled = true,
            isRemovable = true
        )
        val current = getSubscriptions().toMutableList()
        current.add(sub)
        saveSubscriptions(current)
        return sub
    }

    fun removeSubscription(id: String) {
        val current = getSubscriptions().filter { it.id != id }
        val file = File(listsDir, "$id.txt")
        if (file.exists()) file.delete()
        saveSubscriptions(current)
    }

    /**
     * Compiles all active subscriptions + built-in rules into memory
     */
    @Synchronized
    fun compileActiveFilters() {
        val domains = mutableSetOf<String>()
        val keywords = mutableListOf<String>()
        val cosmeticSelectors = mutableSetOf<String>()
        val prunedKeys = mutableSetOf<String>()

        if (useBuiltInFilters()) {
            domains.addAll(DEFAULT_BLOCKED_DOMAINS)
            keywords.addAll(DEFAULT_BLOCKED_URL_KEYWORDS)
            cosmeticSelectors.addAll(DEFAULT_COSMETIC_SELECTORS)
            prunedKeys.addAll(DEFAULT_PRUNED_KEYS)
        }

        val subs = getSubscriptions().filter { it.isEnabled }
        for (sub in subs) {
            val file = File(listsDir, "${sub.id}.txt")
            if (file.exists()) {
                try {
                    val parsed = parseFilterContent(file.readText())
                    domains.addAll(parsed.blockedDomains)
                    keywords.addAll(parsed.blockedUrlKeywords)
                    cosmeticSelectors.addAll(parsed.cosmeticCssSelectors)
                    prunedKeys.addAll(parsed.prunedKeys)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to compile list ${sub.id}", e)
                }
            }
        }

        domainSet = domains.map { it.lowercase() }.toSet()
        keywordList = keywords.map { it.lowercase() }.distinct()
        cachedCss = generateCosmeticCss(cosmeticSelectors.toList())
        cachedScriptlet = generateScriptletJs(prunedKeys.toList().ifEmpty { DEFAULT_PRUNED_KEYS })
    }

    private fun generateCosmeticCss(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        return selectors.joinToString("\n") { selector ->
            """$selector { display: none !important; visibility: hidden !important; height: 0 !important; }"""
        }
    }

    private fun generateScriptletJs(prunedKeys: List<String>): String {
        val keysArray = prunedKeys.joinToString(",") { "\"$it\"" }
        return """
            (function() {
                if (window._sb_adblock_defuser_installed) return;
                window._sb_adblock_defuser_installed = true;

                const PRUNED_KEYS = [$keysArray];

                function pruneAds(obj) {
                    if (!obj || typeof obj !== 'object') return obj;
                    
                    for (let i = 0; i < PRUNED_KEYS.length; i++) {
                        const k = PRUNED_KEYS[i];
                        if (obj[k] !== undefined) {
                            try { delete obj[k]; } catch(e) { obj[k] = undefined; }
                        }
                    }

                    if (obj.playerResponse && typeof obj.playerResponse === 'object') {
                        pruneAds(obj.playerResponse);
                    }

                    return obj;
                }

                // 1. Intercept JSON.parse
                const origParse = JSON.parse;
                JSON.parse = function(text, reviver) {
                    const result = origParse.apply(this, arguments);
                    return pruneAds(result);
                };

                // 2. Intercept Response.prototype.json
                if (window.Response && Response.prototype.json) {
                    const origJson = Response.prototype.json;
                    Response.prototype.json = async function() {
                        const result = await origJson.apply(this, arguments);
                        return pruneAds(result);
                    };
                }

                // 3. Intercept initial player response
                let _initial = window.ytInitialPlayerResponse;
                if (_initial) pruneAds(_initial);
                try {
                    Object.defineProperty(window, 'ytInitialPlayerResponse', {
                        get: function() { return _initial; },
                        set: function(val) {
                            _initial = pruneAds(val);
                        },
                        configurable: true
                    });
                } catch(e) {}
            })();
        """.trimIndent()
    }

    fun isUrlBlocked(host: String?, url: String?): Boolean {
        if (!isAdBlockEnabled() || url == null) return false
        val lowerHost = host?.lowercase() ?: ""
        val lowerUrl = url.lowercase()

        // Critical safeguard: Never block YouTube/Google core media & page domains at the domain level!
        val isProtected = isProtectedDomain(lowerHost)

        if (!isProtected) {
            for (domain in domainSet) {
                if (lowerHost == domain || lowerHost.endsWith(".$domain")) {
                    return true
                }
            }
        }

        // Never block core video playback streams, player code, or basic navigation endpoints
        if (lowerUrl.contains("/videoplayback") || 
            lowerUrl.contains("/s/player/") || 
            lowerUrl.contains("/base.js") ||
            lowerUrl.contains("/watch?") ||
            lowerUrl.contains("/results?") ||
            lowerUrl.contains("/channel/")
        ) {
            return false
        }

        for (keyword in keywordList) {
            if (lowerUrl.contains(keyword)) {
                return true
            }
        }

        return false
    }

    fun getCosmeticCss(): String = cachedCss

    fun getScriptletJs(): String = cachedScriptlet

    suspend fun autoUpdateIfDue() = withContext(Dispatchers.IO) {
        if (!isAutoUpdateOnLaunch()) return@withContext
        val lastUpdate = prefs.getLong("last_filter_update", 0L)
        val twentyFourHours = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastUpdate > twentyFourHours) {
            updateAllFilters()
        }
    }

    suspend fun updateAllFilters(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val subs = getSubscriptions()
            val updatedSubs = mutableListOf<FilterListSubscription>()
            var atLeastOneSuccess = false

            for (sub in subs) {
                if (!sub.isEnabled) {
                    updatedSubs.add(sub)
                    continue
                }

                try {
                    val request = Request.Builder()
                        .url(sub.url)
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        if (body.isNotEmpty()) {
                            val parsed = parseFilterContent(body)
                            val ruleCount = parsed.blockedDomains.size + parsed.cosmeticCssSelectors.size + parsed.prunedKeys.size
                            File(listsDir, "${sub.id}.txt").writeText(body)
                            updatedSubs.add(sub.copy(lastUpdate = System.currentTimeMillis(), ruleCount = ruleCount))
                            atLeastOneSuccess = true
                            continue
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to update list ${sub.name}", e)
                }

                updatedSubs.add(sub)
            }

            saveSubscriptions(updatedSubs)
            prefs.edit().putLong("last_filter_update", System.currentTimeMillis()).apply()

            if (atLeastOneSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update any filter lists"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSubscription(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val subs = getSubscriptions()
            val sub = subs.find { it.id == id } ?: return@withContext Result.failure(Exception("Subscription not found"))
            val request = Request.Builder()
                .url(sub.url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                if (body.isNotEmpty()) {
                    val parsed = parseFilterContent(body)
                    val ruleCount = parsed.blockedDomains.size + parsed.cosmeticCssSelectors.size + parsed.prunedKeys.size
                    File(listsDir, "${sub.id}.txt").writeText(body)
                    val updatedSubs = subs.map {
                        if (it.id == id) it.copy(lastUpdate = System.currentTimeMillis(), ruleCount = ruleCount) else it
                    }
                    saveSubscriptions(updatedSubs)
                    return@withContext Result.success(Unit)
                }
            }
            Result.failure(Exception("HTTP ${response.code}: Failed to download filter list"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFilterContent(content: String): AdBlockFilters {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val parsed = filtersAdapter.fromJson(trimmed)
                if (parsed != null) {
                    return parsed.copy(
                        blockedDomains = parsed.blockedDomains.filter { !isProtectedDomain(it) }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("AdBlockManager", "JSON parsing failed, falling back to line parser", e)
            }
        }

        val domains = mutableListOf<String>()
        val keywords = mutableListOf<String>()
        val cosmeticSelectors = mutableListOf<String>()
        val prunedKeys = mutableListOf<String>()

        trimmed.lines().forEach { rawLine ->
            val l = rawLine.trim()
            if (l.isEmpty() || l.startsWith("!") || l.startsWith("[")) return@forEach

            // 1. Scriptlet / JSON-prune rules
            if (l.contains("##+js(json-prune")) {
                val match = Regex("""json-prune[,\s]+([a-zA-Z0-9_]+)""").find(l)
                match?.groupValues?.getOrNull(1)?.let { prunedKeys.add(it) }
                return@forEach
            }

            // 2. Network domain blocking rules: e.g. ||doubleclick.net^
            if (l.startsWith("||")) {
                val ruleWithoutPrefix = l.removePrefix("||")
                val hasPath = ruleWithoutPrefix.contains("/")
                if (!hasPath) {
                    val domain = ruleWithoutPrefix.substringBefore("^").substringBefore("$").trim().lowercase()
                    if (domain.isNotEmpty() && domain.contains(".") && !isProtectedDomain(domain) && !domain.contains("*")) {
                        domains.add(domain)
                    }
                } else {
                    // Path rule: if it targets youtube and has known ad path keywords, add keyword
                    if (ruleWithoutPrefix.startsWith("youtube.com/") || ruleWithoutPrefix.startsWith("m.youtube.com/")) {
                        val pathPart = ruleWithoutPrefix.substringAfter("/")
                        if (pathPart.contains("pagead") || pathPart.contains("ptracking") || pathPart.contains("api/stats/ads")) {
                            keywords.add(pathPart.substringBefore("*").substringBefore("^").substringBefore("$"))
                        }
                    }
                }
                return@forEach
            }

            // 3. Cosmetic element hiding rules: e.g. youtube.com##.ad-showing
            if (l.contains("##")) {
                val domainPart = l.substringBefore("##").trim()
                val selector = l.substringAfter("##").trim()

                // If domain is specified, it MUST target youtube
                val isYoutubeTarget = domainPart.isEmpty() || 
                    domainPart.split(",").any { 
                        it.trim() == "youtube.com" || it.trim() == "m.youtube.com" || it.trim() == "www.youtube.com" 
                    }

                // If generic (no domain), only accept if specifically matching ad element identifiers
                val isSafeGeneric = domainPart.isEmpty() && (
                    selector.contains("ad-") || 
                    selector.contains("promoted") || 
                    selector.contains("sponsor") || 
                    selector.contains("ytp-ad") || 
                    selector.contains("player-ads")
                )

                if ((isYoutubeTarget || isSafeGeneric) && selector.isNotEmpty() && !selector.startsWith("+js")) {
                    // Reject non-standard / procedural selectors unsupported by standard CSS:
                    val isProcedural = selector.contains(":has(") ||
                            selector.contains(":has-text(") ||
                            selector.contains(":upward(") ||
                            selector.contains(":xpath(") ||
                            selector.contains(":matches-path(") ||
                            selector.contains(":contains(") ||
                            selector.contains("[-ext-") ||
                            selector.contains("{") ||
                            selector.contains("}")

                    if (!isProcedural && selector.length < 200) {
                        cosmeticSelectors.add(selector)
                    }
                }
                return@forEach
            }

            // 4. Regex keywords e.g. /pagead/
            if (l.startsWith("/") && l.endsWith("/") && l.length > 5) {
                val kw = l.removeSurrounding("/")
                if (kw.contains("pagead") || kw.contains("api/stats/ads") || kw.contains("get_midroll_info")) {
                    keywords.add(kw)
                }
            }
        }

        return AdBlockFilters(
            version = 1,
            blockedDomains = domains.distinct(),
            blockedUrlKeywords = keywords.distinct(),
            cosmeticCssSelectors = cosmeticSelectors.distinct(),
            prunedKeys = prunedKeys.distinct()
        )
    }
}

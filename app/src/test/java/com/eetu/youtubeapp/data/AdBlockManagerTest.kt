package com.eetu.youtubeapp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockManagerTest {

    @Test
    fun testUblockOriginRulesDoNotExtractPlayerResponseOrData() {
        val rules = """
            ! uBlock Origin rules
            youtube.com##+js(json-prune, playerResponse.adPlacements playerResponse.playerAds playerResponse.adSlots adPlacements playerAds adSlots legacyImportant)
            m.youtube.com,music.youtube.com##+js(json-prune, playerResponse.adPlacements playerResponse.adSlots)
            otherdomain.com##+js(json-prune, data entries urls value require)
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)

        // Must extract valid ad keys
        assertTrue("Expected adPlacements in prunedKeys", parsed.prunedKeys.contains("adPlacements"))
        assertTrue("Expected adSlots in prunedKeys", parsed.prunedKeys.contains("adSlots"))
        assertTrue("Expected playerAds in prunedKeys", parsed.prunedKeys.contains("playerAds"))
        assertTrue("Expected legacyImportant in prunedKeys", parsed.prunedKeys.contains("legacyImportant"))

        // Must NEVER extract playerResponse or foreign keys that cause black screen
        assertFalse("playerResponse must NEVER be in prunedKeys", parsed.prunedKeys.contains("playerResponse"))
        assertFalse("data must NEVER be in prunedKeys", parsed.prunedKeys.contains("data"))
        assertFalse("entries must NEVER be in prunedKeys", parsed.prunedKeys.contains("entries"))
        assertFalse("urls must NEVER be in prunedKeys", parsed.prunedKeys.contains("urls"))
        assertFalse("value must NEVER be in prunedKeys", parsed.prunedKeys.contains("value"))
        assertFalse("require must NEVER be in prunedKeys", parsed.prunedKeys.contains("require"))
    }

    @Test
    fun testProtectedJsonKeysNeverAllowedInPrunedKeys() {
        val rules = """
            youtube.com##+js(json-prune, playerResponse streamingData videoDetails playabilityStatus responseContext data config enabled)
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)
        assertTrue("All protected keys must be rejected", parsed.prunedKeys.isEmpty())
    }

    @Test
    fun testNonYoutubeJsonPruneRulesIgnored() {
        val rules = """
            some-news.com##+js(json-prune, ad_box paywall)
            randomsite.org##+js(json-prune, banner)
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)
        assertTrue("Non-YouTube json-prune rules must be ignored", parsed.prunedKeys.isEmpty())
    }

    @Test
    fun testCosmeticSelectorsDoNotHideVideoPlayer() {
        val rules = """
            youtube.com##video
            youtube.com###movie_player
            youtube.com##.html5-video-player
            youtube.com##.html5-main-video
            youtube.com##.video-stream
            youtube.com##.player-container
            youtube.com##.ytm-video-player
            youtube.com##.ad-showing
            youtube.com##.ytp-ad-overlay-container
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)

        // Ad elements must be retained
        assertTrue("Expected .ad-showing in cosmetic selectors", parsed.cosmeticCssSelectors.contains(".ad-showing"))
        assertTrue("Expected .ytp-ad-overlay-container in cosmetic selectors", parsed.cosmeticCssSelectors.contains(".ytp-ad-overlay-container"))

        // Player elements must NEVER be hidden
        assertFalse("video element must not be hidden", parsed.cosmeticCssSelectors.contains("video"))
        assertFalse("#movie_player must not be hidden", parsed.cosmeticCssSelectors.contains("#movie_player"))
        assertFalse(".html5-video-player must not be hidden", parsed.cosmeticCssSelectors.contains(".html5-video-player"))
        assertFalse(".html5-main-video must not be hidden", parsed.cosmeticCssSelectors.contains(".html5-main-video"))
        assertFalse(".video-stream must not be hidden", parsed.cosmeticCssSelectors.contains(".video-stream"))
        assertFalse(".player-container must not be hidden", parsed.cosmeticCssSelectors.contains(".player-container"))
        assertFalse(".ytm-video-player must not be hidden", parsed.cosmeticCssSelectors.contains(".ytm-video-player"))
    }

    @Test
    fun testGenericCosmeticRulesStrictlyFiltered() {
        val rules = """
            ##.ad-banner
            ##.promoted-item
            ##.sponsor-card
            ##.ytp-ad-module
            ##.regular-article-content
            ##.sidebar-container
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)

        assertTrue(parsed.cosmeticCssSelectors.contains(".ad-banner"))
        assertTrue(parsed.cosmeticCssSelectors.contains(".promoted-item"))
        assertTrue(parsed.cosmeticCssSelectors.contains(".sponsor-card"))
        assertTrue(parsed.cosmeticCssSelectors.contains(".ytp-ad-module"))

        // Unsafe generic selectors must not be added
        assertFalse(parsed.cosmeticCssSelectors.contains(".regular-article-content"))
        assertFalse(parsed.cosmeticCssSelectors.contains(".sidebar-container"))
    }

    @Test
    fun testProtectedMediaDomainsVsAdDomains() {
        // Media domains must be protected from domain-level blocking
        assertTrue(AdBlockManager.isProtectedDomain("youtube.com"))
        assertTrue(AdBlockManager.isProtectedDomain("m.youtube.com"))
        assertTrue(AdBlockManager.isProtectedDomain("www.youtube.com"))
        assertTrue(AdBlockManager.isProtectedDomain("googlevideo.com"))
        assertTrue(AdBlockManager.isProtectedDomain("rr1---sn-4g5ednle.googlevideo.com"))
        assertTrue(AdBlockManager.isProtectedDomain("i.ytimg.com"))
        assertTrue(AdBlockManager.isProtectedDomain("gstatic.com"))

        // Ad domains must NOT be protected
        assertFalse(AdBlockManager.isProtectedDomain("googleads.g.doubleclick.net"))
        assertFalse(AdBlockManager.isProtectedDomain("ad.doubleclick.net"))
        assertFalse(AdBlockManager.isProtectedDomain("pagead2.googlesyndication.com"))
        assertFalse(AdBlockManager.isProtectedDomain("pubads.g.doubleclick.net"))
    }

    @Test
    fun testNetworkDomainRulesParsedProperly() {
        val rules = """
            ||doubleclick.net^
            ||pagead2.googlesyndication.com^
            ||youtube.com^
            ||googlevideo.com^
        """.trimIndent()

        val parsed = AdBlockManager.parseFilterContent(rules)

        assertTrue("Ad domains must be blocked", parsed.blockedDomains.contains("doubleclick.net"))
        assertTrue("Ad domains must be blocked", parsed.blockedDomains.contains("pagead2.googlesyndication.com"))
        assertFalse("Protected domains must not be blocked", parsed.blockedDomains.contains("youtube.com"))
        assertFalse("Protected domains must not be blocked", parsed.blockedDomains.contains("googlevideo.com"))
    }
}

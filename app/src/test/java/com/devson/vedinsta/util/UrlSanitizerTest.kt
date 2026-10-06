package com.devson.vedinsta.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun testExtractBaseShortcode_standardPost() {
        val url = "https://www.instagram.com/p/C_abc123/?igsh=MWx123abc456"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("C_abc123", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_reel() {
        val url = "https://www.instagram.com/reel/C-xyz789/?utm_source=ig_web_copy_link"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("C-xyz789", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_reelsPlural() {
        val url = "https://www.instagram.com/reels/DA123456789/"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("DA123456789", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_tv() {
        val url = "https://instagram.com/tv/DB_tv987/?igsh=xyz"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("DB_tv987", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_shareLink() {
        val url = "https://www.instagram.com/share/p/DC_share123/?igsh=test"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("DC_share123", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_storyWithMediaId() {
        val url = "https://www.instagram.com/stories/username/3456789012345678901/?igsh=abc"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("3456789012345678901", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_storyHighlightsWithQuery() {
        val url = "https://www.instagram.com/stories/highlights/17987654321098765/?story_media_id=9876543210"
        val shortcode = UrlSanitizer.extractBaseShortcode(url)
        assertEquals("9876543210", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_textContainingUrl() {
        val text = "Hey check this reel out https://www.instagram.com/reel/C_test123/?igsh=123"
        val shortcode = UrlSanitizer.extractBaseShortcode(text)
        assertEquals("C_test123", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_standaloneShortcode() {
        val text = "C_abc12345"
        val shortcode = UrlSanitizer.extractBaseShortcode(text)
        assertEquals("C_abc12345", shortcode)
    }

    @Test
    fun testExtractBaseShortcode_invalidUrl() {
        val text = "https://google.com/search?q=instagram"
        val shortcode = UrlSanitizer.extractBaseShortcode(text)
        assertNull(shortcode)
    }

    @Test
    fun testSanitizeInstagramUrl_stripsTrackingParams() {
        val url = "https://www.instagram.com/reel/C_abc123/?igsh=MWx123abc456&utm_medium=copy_link"
        val sanitized = UrlSanitizer.sanitizeInstagramUrl(url)
        assertEquals("https://www.instagram.com/reel/C_abc123/", sanitized)
    }

    @Test
    fun testSanitizeInstagramUrl_post() {
        val url = "https://www.instagram.com/p/C_abc123/?utm_source=ig_web_copy_link"
        val sanitized = UrlSanitizer.sanitizeInstagramUrl(url)
        assertEquals("https://www.instagram.com/p/C_abc123/", sanitized)
    }
}

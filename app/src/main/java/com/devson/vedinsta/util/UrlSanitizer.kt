package com.devson.vedinsta.util

import java.net.URI

object UrlSanitizer {

    private val URL_FINDER_REGEX = Regex("(https?://[^\\s]+)", RegexOption.IGNORE_CASE)

    // Matches /p/, /reel/, /reels/, /tv/, /share/p/, /share/reel/ followed by the shortcode
    private val POST_OR_REEL_REGEX = Regex(
        "(?:instagr\\.am|instagram\\.com)/(?:share/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)",
        RegexOption.IGNORE_CASE
    )

    // Matches story_media_id query parameter
    private val STORY_MEDIA_ID_REGEX = Regex("[?&]story_media_id=([0-9]+)", RegexOption.IGNORE_CASE)

    // Matches story URL with username or highlights
    private val STORY_URL_REGEX = Regex(
        "(?:instagr\\.am|instagram\\.com)/stories/(?:highlights/)?([A-Za-z0-9_.-]+)(?:/([0-9]+))?",
        RegexOption.IGNORE_CASE
    )

    // Standalone alphanumeric shortcode regex
    private val STANDALONE_SHORTCODE_REGEX = Regex("^[A-Za-z0-9_-]{8,25}$")

    /**
     * Safely strips all query parameters (e.g., ?igsh=...) and extracts the core post ID or shortcode.
     */
    fun extractBaseShortcode(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        // If input is directly an alphanumeric shortcode
        if (STANDALONE_SHORTCODE_REGEX.matches(trimmed) && !trimmed.contains("http", ignoreCase = true)) {
            return trimmed
        }

        // Extract URL substring if embedded within surrounding text
        val targetUrl = URL_FINDER_REGEX.find(trimmed)?.value ?: trimmed

        // 1. Check for standard post, reel, reels, or tv shortcode
        val postMatch = POST_OR_REEL_REGEX.find(targetUrl)
        if (postMatch != null) {
            val shortcode = postMatch.groupValues.getOrNull(1)
            if (!shortcode.isNullOrBlank()) {
                return shortcode
            }
        }

        // 2. Check for story_media_id query parameter
        val storyMediaIdMatch = STORY_MEDIA_ID_REGEX.find(targetUrl)
        if (storyMediaIdMatch != null) {
            val mediaId = storyMediaIdMatch.groupValues.getOrNull(1)
            if (!mediaId.isNullOrBlank()) {
                return mediaId
            }
        }

        // 3. Check for stories path
        val storyMatch = STORY_URL_REGEX.find(targetUrl)
        if (storyMatch != null) {
            val storyId = storyMatch.groupValues.getOrNull(2)
            if (!storyId.isNullOrBlank()) {
                return storyId
            }
            val firstSegment = storyMatch.groupValues.getOrNull(1)
            if (!firstSegment.isNullOrBlank()) {
                return firstSegment
            }
        }

        return null
    }

    /**
     * Produces a clean, sanitized Instagram URL stripped of tracking parameters.
     */
    fun sanitizeInstagramUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""

        if (STANDALONE_SHORTCODE_REGEX.matches(trimmed) && !trimmed.contains("http", ignoreCase = true)) {
            return "https://www.instagram.com/p/$trimmed/"
        }

        val targetUrl = URL_FINDER_REGEX.find(trimmed)?.value ?: trimmed

        val postMatch = POST_OR_REEL_REGEX.find(targetUrl)
        if (postMatch != null) {
            val shortcode = postMatch.groupValues[1]
            val isReel = targetUrl.contains("/reel/", ignoreCase = true) || targetUrl.contains("/reels/", ignoreCase = true)
            val isTv = targetUrl.contains("/tv/", ignoreCase = true)
            return when {
                isReel -> "https://www.instagram.com/reel/$shortcode/"
                isTv -> "https://www.instagram.com/tv/$shortcode/"
                else -> "https://www.instagram.com/p/$shortcode/"
            }
        }

        val storyMatch = STORY_URL_REGEX.find(targetUrl)
        if (storyMatch != null) {
            val storyMediaId = STORY_MEDIA_ID_REGEX.find(targetUrl)?.groupValues?.getOrNull(1)
            val segment1 = storyMatch.groupValues.getOrNull(1).orEmpty()
            val segment2 = storyMatch.groupValues.getOrNull(2).orEmpty()

            val isHighlight = targetUrl.contains("/stories/highlights/", ignoreCase = true)
            return when {
                isHighlight -> {
                    val base = "https://www.instagram.com/stories/highlights/$segment1/"
                    val mediaId = if (segment2.isNotEmpty()) segment2 else storyMediaId
                    if (!mediaId.isNullOrEmpty()) "$base?story_media_id=$mediaId" else base
                }
                segment2.isNotEmpty() -> {
                    "https://www.instagram.com/stories/$segment1/$segment2/"
                }
                else -> {
                    "https://www.instagram.com/stories/$segment1/"
                }
            }
        }

        return targetUrl.substringBefore('?').substringBefore('#').trimEnd('/') + "/"
    }
}

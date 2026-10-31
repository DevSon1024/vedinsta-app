package com.devson.vedinsta.extractor

import android.util.Log
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality

/**
 * Orchestrator managing the waterfall strategy for public (unauthenticated) extractions.
 */
object PublicExtractionOrchestrator {

    private const val TAG = "PublicExtrOrchestrator"

    private val jsonStrategy: PublicExtractionStrategy = JsonApiStrategy()
    private val htmlStrategy: PublicExtractionStrategy = HtmlFallbackStrategy()

    /**
     * Extracts media from a public post URL using the waterfall pattern.
     * First attempts JSON API extraction; if that fails or requires login,
     * falls back to raw HTML metadata scraping.
     *
     * @param url The Instagram post or reel URL.
     * @param qualityPref The desired media quality preference.
     * @param thumbPref The desired thumbnail quality preference.
     * @return The extracted post metadata and media nodes.
     */
    suspend fun extract(
        url: String,
        qualityPref: MediaQuality,
        thumbPref: ThumbnailQuality
    ): ExtractedPost {
        return try {
            Log.d(TAG, "Attempting primary JSON extraction for: $url")
            jsonStrategy.extractMedia(url, qualityPref, thumbPref)
        } catch (e: Exception) {
            Log.w(TAG, "Primary JSON extraction failed: ${e.message}. Cascading to HTML fallback...")
            try {
                htmlStrategy.extractMedia(url, qualityPref, thumbPref)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "HTML fallback extraction failed: ${fallbackEx.message}")
                if (e is AuthRequiredException) {
                    throw e
                }
                throw fallbackEx
            }
        }
    }
}

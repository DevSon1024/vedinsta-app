package com.devson.vedinsta.extractor

import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality

/**
 * Strategy interface for unauthenticated Instagram public media extraction.
 */
interface PublicExtractionStrategy {
    /**
     * Extracts media from a public Instagram post URL using a specific strategy.
     *
     * @param url The Instagram post or reel URL.
     * @param qualityPref The desired media quality preference.
     * @return The extracted post metadata and media nodes.
     */
    suspend fun extractMedia(url: String, qualityPref: MediaQuality): ExtractedPost
}

/**
 * Thrown when an unauthenticated request requires authentication or redirects to login.
 */
class AuthRequiredException(message: String) : Exception(message)


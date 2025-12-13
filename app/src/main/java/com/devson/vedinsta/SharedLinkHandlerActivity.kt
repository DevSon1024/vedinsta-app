package com.devson.vedinsta

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.devson.vedinsta.service.SharedLinkProcessingService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SharedLinkHandlerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle the shared intent
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                handleSharedText(intent)
            }
            intent?.action == Intent.ACTION_VIEW -> {
                handleViewIntent(intent)
            }
            else -> {
                Toast.makeText(this, "Unable to process shared content", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSharedText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
            processInstagramLink(sharedText)
        } ?: run {
            Toast.makeText(this, "No link found in shared content", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleViewIntent(intent: Intent) {
        intent.data?.toString()?.let { url ->
            processInstagramLink(url)
        } ?: run {
            Toast.makeText(this, "Invalid link", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processInstagramLink(text: String) {
        lifecycleScope.launch {
            val instagramUrl = extractInstagramUrl(text)

            if (instagramUrl != null) {
                // Start the background processing service
                val serviceIntent = Intent(this@SharedLinkHandlerActivity, SharedLinkProcessingService::class.java).apply {
                    putExtra(SharedLinkProcessingService.EXTRA_INSTAGRAM_URL, instagramUrl)
                }
                startService(serviceIntent)

                Toast.makeText(
                    this@SharedLinkHandlerActivity,
                    "Processing Instagram link...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@SharedLinkHandlerActivity,
                    "Not a valid Instagram link",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Close the activity immediately
            finish()
        }
    }

    private fun extractInstagramUrl(text: String): String? {
        // Instagram URL patterns
        val patterns = listOf(
            Regex("https?://(?:www\\.)?instagram\\.com/(?:p|reel|tv)/([A-Za-z0-9_-]+)/?.*"),
            Regex("https?://(?:www\\.)?instagr\\.am/(?:p|reel)/([A-Za-z0-9_-]+)/?.*"),
            Regex("instagram\\.com/(?:p|reel|tv)/([A-Za-z0-9_-]+)"),
            Regex("instagr\\.am/(?:p|reel)/([A-Za-z0-9_-]+)")
        )

        patterns.forEach { pattern ->
            pattern.find(text)?.let { matchResult ->
                val shortcode = matchResult.groupValues[1]
                return "https://www.instagram.com/p/$shortcode/"
            }
        }

        return null
    }
}

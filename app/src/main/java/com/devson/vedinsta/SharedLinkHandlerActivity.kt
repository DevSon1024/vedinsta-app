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
        val trimmed = text.trim()

        // 1. Check for Story URLs (User story or Highlight)
        val storyRegex = Regex("(https?://(?:www\\.)?instagram\\.com/stories/[A-Za-z0-9_.-]+(?:/[0-9]+)?/?(?:\\?[^\\s]*)?)", RegexOption.IGNORE_CASE)
        storyRegex.find(trimmed)?.let {
            return it.value
        }

        // 2. Check for Standard Post/Reel/TV URLs
        val postRegex = Regex("(https?://(?:www\\.)?instagr(?:am\\.com|\\.am)/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+/?(?:\\?[^\\s]*)?)", RegexOption.IGNORE_CASE)
        postRegex.find(trimmed)?.let {
            return it.value
        }

        // 3. Fallback generic instagram.com link matcher
        val genericIgRegex = Regex("(https?://(?:www\\.)?instagr(?:am\\.com|\\.am)/[^\\s]+)", RegexOption.IGNORE_CASE)
        genericIgRegex.find(trimmed)?.let {
            return it.value
        }

        return null
    }
}

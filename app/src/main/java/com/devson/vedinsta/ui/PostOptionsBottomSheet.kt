package com.devson.vedinsta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.BottomSheetPostOptionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PostOptionsBottomSheet(
    private val post: DownloadedPost,
    private val onDeletePost: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPostOptionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPostOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.optionOpenProfile.setOnClickListener {
            openInstagramProfile()
            dismiss()
        }

        binding.optionCopyCaption.setOnClickListener {
            copyCaption()
            dismiss()
        }

        binding.optionCopyHashtags.setOnClickListener {
            copyHashtags()
            dismiss()
        }

        binding.optionCopyLink.setOnClickListener {
            copyPostLink()
            dismiss()
        }

        binding.optionOpenInstagram.setOnClickListener {
            openInInstagram()
            dismiss()
        }

        binding.optionDeletePost.setOnClickListener {
            dismiss()
            showDeleteConfirmation()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun openInstagramProfile() {
        val username = post.username
        val instagramUri = Uri.parse("https://www.instagram.com/$username/")

        try {
            // Try to open in Instagram app first
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = instagramUri
                setPackage("com.instagram.android")
            }

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback to browser
                val browserIntent = Intent(Intent.ACTION_VIEW, instagramUri)
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyCaption() {
        val caption = post.caption
        if (!caption.isNullOrEmpty()) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("caption", caption)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Caption copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No caption available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyHashtags() {
        val caption = post.caption
        if (caption.isNullOrEmpty()) {
            Toast.makeText(context, "No caption available", Toast.LENGTH_SHORT).show()
            return
        }

        // Extract hashtags using regex
        val hashtagPattern = "#\\w+".toRegex()
        val hashtags = hashtagPattern.findAll(caption)
            .map { it.value }
            .joinToString(" ")

        if (hashtags.isNotEmpty()) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("hashtags", hashtags)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Hashtags copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No hashtags found in caption", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPostLink() {
        val postId = post.postId
        // Clean URL - remove query parameters
        val cleanUrl = "https://www.instagram.com/p/$postId/"

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("post_link", cleanUrl)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun openInInstagram() {
        val postId = post.postId
        val cleanUrl = "https://www.instagram.com/p/$postId/"
        val instagramUri = Uri.parse(cleanUrl)

        try {
            // Try to open in Instagram app first
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = instagramUri
                setPackage("com.instagram.android")
            }

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback to browser
                val browserIntent = Intent(Intent.ACTION_VIEW, instagramUri)
                startActivity(browserIntent)
                Toast.makeText(context, "Instagram app not found, opening in browser", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening Instagram: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePost()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext().applicationContext)

                var deletedFileCount = 0
                // Delete all media files
                post.mediaPaths.forEach { path ->
                    try {
                        if (!path.startsWith("content://")) {
                            val file = File(path)
                            if (file.exists() && file.delete()) {
                                deletedFileCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with other files
                    }
                }

                // Delete from database
                db.downloadedPostDao().deleteByPostId(post.postId)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Post deleted ($deletedFileCount files)", Toast.LENGTH_SHORT).show()
                    onDeletePost()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error deleting post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PostOptionsBottomSheet"
    }
}
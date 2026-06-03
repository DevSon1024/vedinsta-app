package com.devson.vedinsta.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.viewmodel.MainViewModel
import java.io.File

fun showPostOptions(
    context: Context,
    post: DownloadedPost,
    viewModel: MainViewModel,
    onToggleFavorite: (String) -> Unit,
    isFavorite: Boolean
) {
    val options = arrayOf(
        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
        "Share Post File(s)",
        "Delete from History"
    )

    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Post Options")
        .setItems(options) { _, which ->
            when (which) {
                0 -> onToggleFavorite(post.postId)
                1 -> {
                    if (post.mediaPaths.isNotEmpty()) {
                        val file = File(post.mediaPaths.first())
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = if (file.extension.lowercase() in listOf("mp4", "mov", "avi")) "video/*" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                    } else {
                        Toast.makeText(context, "No media files found to share", Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> {
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Delete Post")
                        .setMessage("Are you sure you want to delete this post and its downloaded files? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteDownloadedPost(post)
                            Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
        .show()
}

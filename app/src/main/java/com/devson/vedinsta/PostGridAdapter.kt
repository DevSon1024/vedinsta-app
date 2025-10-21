package com.devson.vedinsta

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.RoundedCornersTransformation
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ItemPostGridBinding
import java.io.File

class PostsGridAdapter(
    private val onPostClick: (DownloadedPost) -> Unit,
    private val onPostLongClick: (DownloadedPost) -> Unit = {}
) : ListAdapter<DownloadedPost, PostsGridAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: DownloadedPost) {
            val thumbnailFile = File(post.thumbnailPath)

            if (thumbnailFile.exists()) {
                if (post.hasVideo && post.thumbnailPath.endsWith(".mp4")) {
                    // Load video thumbnail
                    loadVideoThumbnail(thumbnailFile)
                } else {
                    // Load image normally
                    binding.ivPostThumbnail.load(thumbnailFile) {
                        placeholder(R.drawable.placeholder_image)
                        error(R.drawable.placeholder_image)
                        crossfade(300)
                        size(Size.ORIGINAL)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } else {
                // File doesn't exist, show placeholder
                binding.ivPostThumbnail.load(R.drawable.placeholder_image)
            }

            // Show play button for videos
            binding.ivPlayButton.visibility = if (post.hasVideo) View.VISIBLE else View.GONE

            // Show multiple items indicator if more than 1 media item
            binding.ivMultipleIndicator.visibility = if (post.totalImages > 1) View.VISIBLE else View.GONE

            // Handle clicks
            binding.root.setOnClickListener {
                onPostClick(post)
            }

            binding.root.setOnLongClickListener {
                onPostLongClick(post)
                true
            }
        }

        private fun loadVideoThumbnail(videoFile: File) {
            try {
                // Generate video thumbnail
                val thumbnail = ThumbnailUtils.createVideoThumbnail(
                    videoFile.absolutePath,
                    MediaStore.Images.Thumbnails.MICRO_KIND
                )

                if (thumbnail != null) {
                    binding.ivPostThumbnail.load(thumbnail) {
                        crossfade(300)
                        transformations(RoundedCornersTransformation(8f))
                    }
                } else {
                    // Fallback to Coil for video thumbnail
                    binding.ivPostThumbnail.load(videoFile) {
                        placeholder(R.drawable.ic_play_circle)
                        error(R.drawable.ic_play_circle)
                        crossfade(300)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } catch (e: Exception) {
                // Fallback to play icon
                binding.ivPostThumbnail.load(R.drawable.ic_play_circle)
            }
        }
    }
}

class PostDiffCallback : DiffUtil.ItemCallback<DownloadedPost>() {
    override fun areItemsTheSame(oldItem: DownloadedPost, newItem: DownloadedPost): Boolean {
        return oldItem.postId == newItem.postId
    }

    override fun areContentsTheSame(oldItem: DownloadedPost, newItem: DownloadedPost): Boolean {
        return oldItem == newItem
    }
}

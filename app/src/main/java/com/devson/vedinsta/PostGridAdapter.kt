package com.devson.vedinsta

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
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
                    // Load video thumbnail using modern approach
                    loadVideoThumbnailModern(thumbnailFile)
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

            // Handle clicks - Launch PostViewActivity
            binding.root.setOnClickListener {
                val intent = PostViewActivity.createIntent(binding.root.context, post)
                binding.root.context.startActivity(intent)
            }

            binding.root.setOnLongClickListener {
                onPostLongClick(post)
                true
            }
        }

        private fun loadVideoThumbnailModern(videoFile: File) {
            try {
                // Use MediaMetadataRetriever instead of deprecated ThumbnailUtils
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)

                // Get frame at 1 second (1000000 microseconds)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        1000000, // 1 second
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        256, // width
                        256  // height
                    )
                } else {
                    retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                retriever.release()

                if (bitmap != null) {
                    binding.ivPostThumbnail.load(bitmap) {
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

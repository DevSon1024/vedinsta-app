package com.devson.vedinsta

import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import coil.transform.RoundedCornersTransformation
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ItemPostGridBinding
import java.io.File

private const val VIEW_TYPE_POST = 1
private const val VIEW_TYPE_DOWNLOADING = 2

class PostsGridAdapter(
    private val onPostClick: (DownloadedPost) -> Unit,
    private val onPostLongClick: (DownloadedPost) -> Unit = {}
) : ListAdapter<GridPostItem, RecyclerView.ViewHolder>(GridItemDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.isDownloading) VIEW_TYPE_DOWNLOADING else VIEW_TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemPostGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return when (viewType) {
            VIEW_TYPE_DOWNLOADING -> DownloadingViewHolder(binding)
            else -> PostViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is PostViewHolder -> item.post?.let { holder.bind(it) }
            is DownloadingViewHolder -> holder.bind()
        }
    }

    inner class PostViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: DownloadedPost) {
            val thumbnailFile = File(post.thumbnailPath)

            if (thumbnailFile.exists()) {
                if (post.hasVideo && post.thumbnailPath.endsWith(".mp4")) {
                    loadVideoThumbnailModern(thumbnailFile)
                } else {
                    binding.ivPostThumbnail.load(thumbnailFile) {
                        placeholder(R.drawable.placeholder_image)
                        error(R.drawable.placeholder_image)
                        crossfade(300)
                        size(Size.ORIGINAL)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } else {
                binding.ivPostThumbnail.load(R.drawable.placeholder_image)
            }

            binding.ivPlayButton.visibility = if (post.hasVideo) View.VISIBLE else View.GONE
            binding.ivMultipleIndicator.visibility = if (post.totalImages > 1) View.VISIBLE else View.GONE

            // Hide loading overlay for real posts
            binding.loadingOverlay.visibility = View.GONE

            binding.root.setOnClickListener {
                onPostClick(post)
            }

            binding.root.setOnLongClickListener {
                onPostLongClick(post)
                true
            }
        }

        private fun loadVideoThumbnailModern(videoFile: File) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
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
                    binding.ivPostThumbnail.load(videoFile) {
                        placeholder(R.drawable.ic_play_circle)
                        error(R.drawable.ic_play_circle)
                        crossfade(300)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } catch (_: Exception) {
                binding.ivPostThumbnail.load(R.drawable.ic_play_circle)
            }
        }
    }

    inner class DownloadingViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            // Dim background and show spinner
            binding.ivPostThumbnail.setImageResource(R.drawable.placeholder_image)
            binding.ivPlayButton.visibility = View.GONE
            binding.ivMultipleIndicator.visibility = View.GONE
            binding.loadingOverlay.visibility = View.VISIBLE

            // Disable interactions while downloading
            binding.root.setOnClickListener { /* no-op */ }
            binding.root.setOnLongClickListener { true }
        }
    }
}

class GridItemDiffCallback : DiffUtil.ItemCallback<GridPostItem>() {
    override fun areItemsTheSame(oldItem: GridPostItem, newItem: GridPostItem): Boolean {
        // Match on DB id when available, else use tempId for dummy
        val oldKey = oldItem.post?.postId ?: oldItem.tempId
        val newKey = newItem.post?.postId ?: newItem.tempId
        return oldKey == newKey
    }

    override fun areContentsTheSame(oldItem: GridPostItem, newItem: GridPostItem): Boolean {
        return oldItem == newItem
    }
}

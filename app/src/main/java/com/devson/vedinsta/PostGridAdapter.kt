package com.devson.vedinsta

import android.util.Log // Import Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // Import Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import coil.transform.RoundedCornersTransformation
// Import Coil video decoder AND the extension function
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis // <-- Correct import for extension function

import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ItemPostGridBinding
import com.devson.vedinsta.ui.HomeFragment // Import for payload constant
import java.io.File

private const val VIEW_TYPE_POST = 1
private const val VIEW_TYPE_DOWNLOADING = 2

class PostsGridAdapter(
    private val onPostClick: (DownloadedPost) -> Unit,
    private val onPostLongClick: (DownloadedPost) -> Unit = {}
) : ListAdapter<GridPostItem, RecyclerView.ViewHolder>(GridItemDiffCallback()) {

    // ... (getItemViewType, onCreateViewHolder, onBindViewHolder, onBindViewHolder with payload remain the same) ...
    override fun getItemViewType(position: Int): Int {
        return try {
            val item = getItem(position)
            if (item.isDownloading) VIEW_TYPE_DOWNLOADING else VIEW_TYPE_POST
        } catch (e: IndexOutOfBoundsException) {
            Log.e("PostsGridAdapter", "getItemViewType IndexOutOfBounds: $position", e)
            VIEW_TYPE_POST // Default
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemPostGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return when (viewType) {
            VIEW_TYPE_DOWNLOADING -> DownloadingViewHolder(binding)
            else -> PostViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val item = getItem(position)
            when (holder) {
                is PostViewHolder -> item.post?.let { holder.bind(it) }
                is DownloadingViewHolder -> holder.bind(item)
            }
        } catch (e: IndexOutOfBoundsException) {
            Log.e("PostsGridAdapter", "onBindViewHolder IndexOutOfBounds: $position", e)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads) // Full bind
        } else {
            payloads.forEach { payload ->
                if (payload == HomeFragment.PAYLOAD_PROGRESS && holder is DownloadingViewHolder) {
                    try {
                        val item = getItem(position)
                        holder.updateProgress(item.downloadProgress)
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e("PostsGridAdapter", "onBindViewHolder payload IndexOutOfBounds: $position", e)
                    }
                }
            }
        }
    }


    inner class PostViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: DownloadedPost) {
            if (post.thumbnailPath.isBlank()) {
                binding.ivPostThumbnail.load(R.drawable.placeholder_image)
                binding.ivPlayButton.visibility = View.GONE
                binding.ivMultipleIndicator.visibility = View.GONE
                binding.loadingOverlay.visibility = View.GONE
                binding.overlayDim.alpha = 0.1f
                return
            }

            val thumbnailFile = File(post.thumbnailPath)

            if (thumbnailFile.exists()) {
                val isVideoThumbnail = post.hasVideo

                if (isVideoThumbnail && post.thumbnailPath.endsWith(".mp4", ignoreCase = true)) {
                    loadVideoThumbnailWithCoil(thumbnailFile)
                } else {
                    binding.ivPostThumbnail.load(thumbnailFile) {
                        placeholder(R.drawable.placeholder_image)
                        error(R.drawable.placeholder_image)
                        crossfade(true)
                        size(Size.ORIGINAL)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } else {
                Log.w("PostViewHolder", "Thumbnail file does not exist: ${post.thumbnailPath}")
                binding.ivPostThumbnail.load(R.drawable.placeholder_image) {
                    error(if (post.hasVideo) R.drawable.ic_play_circle else R.drawable.placeholder_image)
                }
            }

            binding.ivPlayButton.visibility = if (post.hasVideo) View.VISIBLE else View.GONE
            binding.ivMultipleIndicator.visibility = if (post.totalImages > 1) View.VISIBLE else View.GONE
            binding.loadingOverlay.visibility = View.GONE
            binding.overlayDim.alpha = 0.1f

            binding.root.setOnClickListener { onPostClick(post) }
            binding.root.setOnLongClickListener { onPostLongClick(post); true }
        }

        private fun loadVideoThumbnailWithCoil(videoFile: File) {
            binding.ivPostThumbnail.load(videoFile) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.ic_play_circle)
                crossfade(true)
                size(Size.ORIGINAL)
                transformations(RoundedCornersTransformation(8f))
                decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                videoFrameMillis(1000) // <-- Correct extension function usage
            }
        }
    }

    inner class DownloadingViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GridPostItem) {
            binding.ivPostThumbnail.setImageResource(R.drawable.placeholder_image)
            binding.overlayDim.alpha = 0.6f
            binding.ivPlayButton.visibility = View.GONE
            binding.ivMultipleIndicator.visibility = View.GONE
            binding.loadingOverlay.visibility = View.VISIBLE

            updateProgress(item.downloadProgress)

            binding.root.setOnClickListener {
                Toast.makeText(binding.root.context, "Download in progress...", Toast.LENGTH_SHORT).show()
            }
            binding.root.setOnLongClickListener {
                item.post?.let { onPostLongClick(it) }
                true
            }
        }

        fun updateProgress(progress: Int) {
            val isIndeterminate = progress < 0
            binding.progressLoading.isIndeterminate = isIndeterminate
            binding.progressLoading.progress = if (!isIndeterminate) progress else 0
        }
    }
}

// --- GridItemDiffCallback (Remains Corrected) ---
class GridItemDiffCallback : DiffUtil.ItemCallback<GridPostItem>() {
    override fun areItemsTheSame(oldItem: GridPostItem, newItem: GridPostItem): Boolean {
        return oldItem.uniqueKey == newItem.uniqueKey
    }

    override fun areContentsTheSame(oldItem: GridPostItem, newItem: GridPostItem): Boolean {
        return oldItem.isDownloading == newItem.isDownloading &&
                oldItem.downloadProgress == newItem.downloadProgress &&
                (oldItem.isDownloading || oldItem.post == newItem.post)
    }

    override fun getChangePayload(oldItem: GridPostItem, newItem: GridPostItem): Any? {
        if (oldItem.isDownloading && newItem.isDownloading && oldItem.downloadProgress != newItem.downloadProgress) {
            return HomeFragment.PAYLOAD_PROGRESS
        }
        if (oldItem.isDownloading && !newItem.isDownloading) {
            return null
        }
        return super.getChangePayload(oldItem, newItem)
    }
}
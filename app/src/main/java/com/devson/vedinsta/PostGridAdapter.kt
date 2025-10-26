package com.devson.vedinsta

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import coil.transform.RoundedCornersTransformation
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ItemPostGridBinding
import com.devson.vedinsta.ui.HomeFragment
import java.io.File
import kotlinx.coroutines.*

private const val VIEW_TYPE_POST = 1
private const val VIEW_TYPE_DOWNLOADING = 2

class PostsGridAdapter(
    private val onPostClick: (DownloadedPost) -> Unit,
    private val onPostLongClick: (DownloadedPost) -> Unit = {}
) : ListAdapter<GridPostItem, RecyclerView.ViewHolder>(GridItemDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return try {
            val item = getItem(position)
            if (item.isDownloading) VIEW_TYPE_DOWNLOADING else VIEW_TYPE_POST
        } catch (e: IndexOutOfBoundsException) {
            Log.e("PostsGridAdapter", "getItemViewType IndexOutOfBounds: $position", e)
            VIEW_TYPE_POST
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
            super.onBindViewHolder(holder, position, payloads)
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
            // Reset all states first
            resetViewStates()

            // Load thumbnail image
            loadThumbnailImage(post)

            // Set overlay icons - this is the key fix
            setupOverlayIcons(post)

            // Set click listeners
            binding.root.setOnClickListener { onPostClick(post) }
            binding.root.setOnLongClickListener { onPostLongClick(post); true }

            // Hide loading overlay and set dim
            binding.loadingOverlay.visibility = View.GONE
            binding.overlayDim.alpha = 0.3f // Increased for better icon visibility
        }

        private fun resetViewStates() {
            binding.ivPlayButton.visibility = View.GONE
            binding.ivMultipleIndicator.visibility = View.GONE
        }

        private fun setupOverlayIcons(post: DownloadedPost) {
            // Determine which icon to show
            val isMultiple = post.totalImages > 1 || post.mediaPaths.size > 1

            // Check if post has video - use multiple detection methods
            val hasVideo = post.hasVideo ||
                    post.mediaPaths.any { isVideoFile(it) } ||
                    isVideoFile(post.thumbnailPath)

            Log.d("PostGridAdapter", "setupOverlayIcons - isMultiple: $isMultiple, hasVideo: $hasVideo (post.hasVideo: ${post.hasVideo}), totalImages: ${post.totalImages}, mediaPaths: ${post.mediaPaths}")

            // Reset both views
            binding.ivMultipleIndicator.visibility = View.GONE
            binding.ivPlayButton.visibility = View.GONE

            when {
                isMultiple -> {
                    // Show stack icon for multiple items in top-right
                    binding.ivMultipleIndicator.apply {
                        visibility = View.VISIBLE
                        setImageResource(R.drawable.ic_multiple_images_filled)
                        imageTintList = null
                        alpha = 1f
                        Log.d("PostGridAdapter", "Showing multiple indicator")
                    }
                }
                hasVideo -> {
                    // Show play icon for single video in CENTER
                    binding.ivPlayButton.apply {
                        visibility = View.VISIBLE
                        setImageResource(R.drawable.ic_play_circle_filled)
                        imageTintList = null
                        alpha = 1f
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        Log.d("PostGridAdapter", "Showing play button")
                    }
                }
                else -> {
                    // Single image - no icon
                    Log.d("PostGridAdapter", "No icon needed - single image")
                }
            }
        }

        private fun loadThumbnailImage(post: DownloadedPost) {
            if (post.thumbnailPath.isBlank()) {
                binding.ivPostThumbnail.load(R.drawable.placeholder_image)
                return
            }

            val thumbnailFile = File(post.thumbnailPath)

            if (thumbnailFile.exists() && isImageFile(post.thumbnailPath)) {
                loadImageThumbnail(thumbnailFile)
            }
            else if (post.hasVideo && isVideoFile(post.thumbnailPath)) {
                loadVideoThumbnail(post)
            }
            else if (post.mediaPaths.isNotEmpty()) {
                val firstMediaFile = File(post.mediaPaths.first())
                if (firstMediaFile.exists()) {
                    if (isImageFile(firstMediaFile.path)) {
                        loadImageThumbnail(firstMediaFile)
                    } else if (isVideoFile(firstMediaFile.path)) {
                        loadVideoThumbnailFromFile(firstMediaFile)
                    } else {
                        loadPlaceholder()
                    }
                } else {
                    loadPlaceholder()
                }
            } else {
                loadPlaceholder()
            }
        }

        private fun isImageFile(path: String): Boolean {
            return path.matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)$", RegexOption.IGNORE_CASE))
        }

        private fun isVideoFile(path: String): Boolean {
            return path.matches(Regex(".*\\.(mp4|avi|mov|mkv|webm|3gp)$", RegexOption.IGNORE_CASE))
        }

        private fun loadImageThumbnail(file: File) {
            binding.ivPostThumbnail.load(file) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(true)
                size(Size.ORIGINAL)
                transformations(RoundedCornersTransformation(8f))
            }
        }

        private fun loadVideoThumbnail(post: DownloadedPost) {
            val videoFile = post.mediaPaths.find { isVideoFile(it) }?.let { File(it) }

            if (videoFile?.exists() == true) {
                loadVideoThumbnailFromFile(videoFile)
            } else {
                val thumbnailAsVideo = File(post.thumbnailPath)
                if (thumbnailAsVideo.exists() && isVideoFile(post.thumbnailPath)) {
                    loadVideoThumbnailFromFile(thumbnailAsVideo)
                } else {
                    loadPlaceholder(isVideo = true)
                }
            }
        }

        private fun loadVideoThumbnailFromFile(videoFile: File) {
            try {
                binding.ivPostThumbnail.load(videoFile) {
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.ic_play_circle_filled)
                    crossfade(true)
                    size(Size.ORIGINAL)
                    transformations(RoundedCornersTransformation(8f))
                    decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                    videoFrameMillis(1000)
                }
            } catch (e: Exception) {
                Log.e("PostViewHolder", "Error loading video thumbnail with Coil", e)
                loadVideoThumbnailWithThumbnailUtils(videoFile)
            }
        }

        private fun loadVideoThumbnailWithThumbnailUtils(videoFile: File) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = ThumbnailUtils.createVideoThumbnail(
                        videoFile.absolutePath,
                        android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                    )

                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.ivPostThumbnail.setImageBitmap(bitmap)
                        } else {
                            loadPlaceholder(isVideo = true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PostViewHolder", "Error creating video thumbnail", e)
                    withContext(Dispatchers.Main) {
                        loadPlaceholder(isVideo = true)
                    }
                }
            }
        }

        private fun loadPlaceholder(isVideo: Boolean = false) {
            val placeholderRes = if (isVideo) R.drawable.ic_play_circle_filled else R.drawable.placeholder_image
            binding.ivPostThumbnail.load(placeholderRes) {
                transformations(RoundedCornersTransformation(8f))
            }
        }
    }

    inner class DownloadingViewHolder(
        private val binding: ItemPostGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GridPostItem) {
            binding.ivPostThumbnail.setImageResource(R.drawable.placeholder_image)
            binding.overlayDim.alpha = 0.6f

            // Hide overlay icons during download
            binding.ivPlayButton.visibility = View.GONE
            binding.ivMultipleIndicator.visibility = View.GONE

            // Show loading overlay
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
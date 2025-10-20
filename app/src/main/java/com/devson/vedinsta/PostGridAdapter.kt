package com.devson.vedinsta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
            // Load thumbnail image using Coil
            val thumbnailFile = File(post.thumbnailPath)
            if (thumbnailFile.exists()) {
                binding.ivPostThumbnail.load(thumbnailFile) {
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                    crossfade(true)
                }
            } else {
                binding.ivPostThumbnail.load(android.R.drawable.ic_menu_gallery)
            }

            // Show play button for videos
            binding.ivPlayButton.visibility = if (post.hasVideo) View.VISIBLE else View.GONE

            // Show multiple items indicator if more than 1 image
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

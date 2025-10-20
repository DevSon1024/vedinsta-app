package com.devson.vedinsta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.devson.vedinsta.databinding.ItemPostGridBinding

data class PostItem(
    val id: String,
    val thumbnailUrl: String,
    val isVideo: Boolean = false,
    val hasMultipleItems: Boolean = false,
    val downloadDate: Long = System.currentTimeMillis()
)

class PostsGridAdapter(
    private val onPostClick: (PostItem) -> Unit
) : ListAdapter<PostItem, PostsGridAdapter.PostViewHolder>(PostDiffCallback()) {

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

        fun bind(post: PostItem) {
            // Load thumbnail image using Coil (which you already have)
            binding.ivPostThumbnail.load(post.thumbnailUrl) {
                placeholder(android.R.drawable.ic_menu_gallery) // Using system drawable as placeholder
                error(android.R.drawable.ic_menu_gallery)
                crossfade(true)
            }

            // Show play button for videos
            binding.ivPlayButton.visibility = if (post.isVideo) View.VISIBLE else View.GONE

            // Show multiple items indicator
            binding.ivMultipleIndicator.visibility = if (post.hasMultipleItems) View.VISIBLE else View.GONE

            // Handle click
            binding.root.setOnClickListener {
                onPostClick(post)
            }
        }
    }
}

class PostDiffCallback : DiffUtil.ItemCallback<PostItem>() {
    override fun areItemsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
        return oldItem == newItem
    }
}

// src/main/java/com/devson/vedinsta/ImageAdapter.kt
package com.devson.vedinsta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.devson.vedinsta.databinding.ItemImageCardBinding

class ImageAdapter(private val mediaList: MutableList<ImageCard>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ItemImageCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding =
            ItemImageCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val currentItem = mediaList[position]
        holder.binding.apply {
            // Load the image using Coil
            ivMedia.load(currentItem.url) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_dialog_alert)
            }

            // Show play icon for videos
            ivPlayIcon.visibility = if (currentItem.type == "video") View.VISIBLE else View.GONE

            // Handle checkbox state
            checkboxSelect.isChecked = currentItem.isSelected
            checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                currentItem.isSelected = isChecked
            }
        }
    }

    override fun getItemCount() = mediaList.size

    fun getSelectedItems(): List<ImageCard> {
        return mediaList.filter { it.isSelected }
    }

    fun selectAll(select: Boolean) {
        mediaList.forEach { it.isSelected = select }
        notifyDataSetChanged()
    }
}
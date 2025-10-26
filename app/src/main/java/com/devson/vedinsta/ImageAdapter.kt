package com.devson.vedinsta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.decode.VideoFrameDecoder // Import VideoFrameDecoder
import coil.load
import coil.request.videoFrameMillis // Import videoFrameMillis
import coil.size.Size
import com.devson.vedinsta.databinding.ItemImageSelectionBinding // Use ViewBinding

class ImageAdapter(
    private val mediaList: List<ImageCard>,
    val selectedItems: MutableSet<Int>, // Make it accessible from Activity
    private val onSelectionChanged: (position: Int, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // Use ViewBinding
        val binding = ItemImageSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(mediaList[position], position)
    }

    override fun getItemCount(): Int = mediaList.size

    // Keep this function if needed elsewhere, otherwise rely on selectedItems directly
    fun getSelectedItemsData(): List<ImageCard> {
        return selectedItems.mapNotNull { index -> mediaList.getOrNull(index) }.sortedBy { it.url } // Sort for consistency
    }

    inner class ImageViewHolder(private val binding: ItemImageSelectionBinding) : // Use ViewBinding
        RecyclerView.ViewHolder(binding.root) {

        fun bind(media: ImageCard, position: Int) {
            val isSelected = selectedItems.contains(position)
            val isVideo = media.type == "video" // Check if it's a video

            binding.apply { // Use binding directly
                // Load image or video thumbnail
                ivMedia.load(media.url) {
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.placeholder_image) // Fallback placeholder
                    crossfade(300)
                    size(Size.ORIGINAL) // Load original size for better quality in carousel

                    // **** Add Video Frame Decoding ****
                    if (isVideo) {
                        decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                        videoFrameMillis(1000) // Load frame at 1 second (or 0 for the very first)
                    }
                    // **** End Video Frame Decoding ****
                }

                // Show video icon overlay if it's a video
                ivVideoIcon.visibility = if (isVideo) View.VISIBLE else View.GONE

                // Update selection state visuals
                ivSelectionIcon.setImageResource(
                    if (isSelected) R.drawable.ic_check_circle_filled // Filled green check
                    else R.drawable.ic_check_circle_outline // Outline grey check
                )

                // Update selection overlay opacity
                overlayView.alpha = if (isSelected) 0.35f else 0.0f // Slightly more visible overlay

                // Click listeners for the whole item and the check icon
                root.setOnClickListener { toggleSelection(position) }
                ivSelectionIcon.setOnClickListener { toggleSelection(position) }
            }
        }

        private fun toggleSelection(position: Int) {
            val currentlySelected = selectedItems.contains(position)
            val newSelectionState = !currentlySelected

            if (newSelectionState) {
                selectedItems.add(position)
            } else {
                selectedItems.remove(position)
            }
            // Notify the adapter to redraw the item with the new state
            notifyItemChanged(position)
            // Inform the activity about the change
            onSelectionChanged.invoke(position, newSelectionState)
        }
    }
}
package com.devson.vedinsta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size

class ImageAdapter(
    private val mediaList: List<ImageCard>,
    private val selectedItems: MutableSet<Int> = mutableSetOf(),
    private val onSelectionChanged: ((position: Int, isSelected: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_selection, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(mediaList[position], position)
    }

    override fun getItemCount(): Int = mediaList.size

    fun getSelectedItems(): List<ImageCard> {
        return selectedItems.map { mediaList[it] }
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivMedia: ImageView = itemView.findViewById(R.id.ivMedia)
        private val ivVideoIcon: ImageView = itemView.findViewById(R.id.ivVideoIcon)
        private val ivSelectionIcon: ImageView = itemView.findViewById(R.id.ivSelectionIcon)
        private val overlayView: View = itemView.findViewById(R.id.overlayView)

        fun bind(media: ImageCard, position: Int) {
            val isSelected = selectedItems.contains(position)

            // Load image/video thumbnail
            if (media.type == "video") {
                ivMedia.load(media.url) {
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.placeholder_image)
                    crossfade(300)
                    size(Size.ORIGINAL)
                }
                ivVideoIcon.visibility = View.VISIBLE
            } else {
                ivMedia.load(media.url) {
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.placeholder_image)
                    crossfade(300)
                    size(Size.ORIGINAL)
                }
                ivVideoIcon.visibility = View.GONE
            }

            // Update selection state
            ivSelectionIcon.setImageResource(
                if (isSelected) R.drawable.ic_check_circle_filled
                else R.drawable.ic_check_circle_outline
            )

            // Add selection overlay
            overlayView.alpha = if (isSelected) 0.3f else 0.0f

            // Click listeners
            itemView.setOnClickListener {
                val newSelectionState = !isSelected
                onSelectionChanged?.invoke(position, newSelectionState)
                notifyItemChanged(position)
            }

            ivSelectionIcon.setOnClickListener {
                val newSelectionState = !isSelected
                onSelectionChanged?.invoke(position, newSelectionState)
                notifyItemChanged(position)
            }
        }
    }
}

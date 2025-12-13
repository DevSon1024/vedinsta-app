package com.devson.vedinsta.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MediaSelectionAdapter : ListAdapter<MediaSelectionAdapter.MediaItem, MediaSelectionAdapter.ViewHolder>(MediaDiffCallback()) {

    data class MediaItem(
        val url: String,
        val type: String,
        val index: Int,
        var isSelected: Boolean = false
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.mediaImageView)
        val checkBox: CheckBox = view.findViewById(R.id.selectionCheckBox)
        val videoIndicator: ImageView = view.findViewById(R.id.videoIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        // Load thumbnail asynchronously
        loadImage(holder.imageView, item.url)

        // Show video indicator
        holder.videoIndicator.visibility = if (item.type == "video") View.VISIBLE else View.GONE

        // Set checkbox state
        holder.checkBox.isChecked = item.isSelected

        // Handle item click
        holder.itemView.setOnClickListener {
            item.isSelected = !item.isSelected
            holder.checkBox.isChecked = item.isSelected
            notifyItemChanged(position)
        }

        // Handle checkbox click
        holder.checkBox.setOnClickListener {
            item.isSelected = holder.checkBox.isChecked
            notifyItemChanged(position)
        }
    }

    private fun loadImage(imageView: ImageView, url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
    }

    fun getSelectedItems(): List<MediaItem> {
        return currentList.filter { it.isSelected }
    }

    fun getSelectedCount(): Int {
        return currentList.count { it.isSelected }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}
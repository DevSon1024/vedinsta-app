package com.devson.vedinsta.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.R
import com.devson.vedinsta.databinding.ItemDownloadStatusBinding

data class DownloadItem(
    val id: String,
    val filename: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val url: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED, PAUSED
}

class DownloadStatusAdapter(
    private val onItemClick: (DownloadItem) -> Unit = {},
    private val onRetryClick: (DownloadItem) -> Unit = {}
) : ListAdapter<DownloadItem, DownloadStatusAdapter.ViewHolder>(DownloadDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadStatusBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemDownloadStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.apply {
                tvFilename.text = item.filename
                progressBar.progress = item.progress

                when (item.status) {
                    DownloadStatus.PENDING -> {
                        tvStatus.text = "Pending"
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = true
                        ivStatusIcon.setImageResource(R.drawable.ic_pending)
                        root.alpha = 0.7f
                    }
                    DownloadStatus.DOWNLOADING -> {
                        tvStatus.text = "Downloading ${item.progress}%"
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = false
                        progressBar.progress = item.progress
                        ivStatusIcon.setImageResource(R.drawable.ic_downloading)
                        root.alpha = 1.0f
                    }
                    DownloadStatus.COMPLETED -> {
                        tvStatus.text = "Completed"
                        progressBar.visibility = View.GONE
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        root.alpha = 1.0f
                    }
                    DownloadStatus.FAILED -> {
                        tvStatus.text = "Failed"
                        progressBar.visibility = View.GONE
                        ivStatusIcon.setImageResource(R.drawable.ic_error)
                        root.alpha = 0.8f

                        // Show retry button for failed downloads
                        root.setOnClickListener { onRetryClick(item) }
                    }
                    DownloadStatus.PAUSED -> {
                        tvStatus.text = "Paused"
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = false
                        ivStatusIcon.setImageResource(R.drawable.ic_pause)
                        root.alpha = 0.8f
                    }
                }

                root.setOnClickListener { onItemClick(item) }
            }
        }
    }
}

class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
    override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem == newItem
    }
}

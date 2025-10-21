package com.devson.vedinsta.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val onNotificationClick: (NotificationEntity) -> Unit
) : ListAdapter<NotificationEntity, NotificationAdapter.NotificationViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationEntity) {
            binding.apply {
                textTitle.text = notification.title
                textMessage.text = notification.message
                textTime.text = formatTime(notification.timestamp)

                // Set icon based on notification type
                imageIcon.setImageResource(when (notification.type) {
                    NotificationType.DOWNLOAD_STARTED -> android.R.drawable.stat_sys_download
                    NotificationType.DOWNLOAD_COMPLETED -> android.R.drawable.stat_sys_download_done
                    NotificationType.DOWNLOAD_FAILED -> android.R.drawable.stat_notify_error
                    NotificationType.DOWNLOAD_PROGRESS -> android.R.drawable.stat_sys_download
                    NotificationType.SYSTEM_INFO -> android.R.drawable.ic_dialog_info
                })

                // Show unread indicator
                unreadIndicator.visibility = if (notification.isRead)
                    android.view.View.GONE else android.view.View.VISIBLE

                // Set background for unread notifications
                root.alpha = if (notification.isRead) 0.7f else 1.0f

                root.setOnClickListener {
                    onNotificationClick(notification)
                }
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                diff < 604800_000 -> "${diff / 86400_000}d ago"
                else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationEntity>() {
        override fun areItemsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) =
            oldItem == newItem
    }
}
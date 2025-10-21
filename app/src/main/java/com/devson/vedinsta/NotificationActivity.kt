package com.devson.vedinsta

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.devson.vedinsta.adapters.NotificationAdapter
import com.devson.vedinsta.databinding.ActivityNotificationBinding
import com.devson.vedinsta.viewmodel.NotificationViewModel

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var viewModel: NotificationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupRecyclerView()
        setupViewModel()
    }

    private fun setupTopBar() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter { notification ->
            // Handle notification click - mark as read
            viewModel.markAsRead(notification.id)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = notificationAdapter
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        viewModel.allNotifications.observe(this) { notifications ->
            notificationAdapter.submitList(notifications)
            updateEmptyState(notifications.isEmpty())
        }

        viewModel.unreadCount.observe(this) { count ->
            if (count > 0) {
                binding.title.text = "Notifications ($count)"
            } else {
                binding.title.text = "Notifications"
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }
}

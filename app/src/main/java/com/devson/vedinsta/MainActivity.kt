package com.devson.vedinsta

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.devson.vedinsta.databinding.ActivityMainBinding
import com.devson.vedinsta.ui.DownloadsFragment
import com.devson.vedinsta.ui.FavoritesFragment
import com.devson.vedinsta.ui.HomeFragment
import com.devson.vedinsta.ui.SettingsFragment
import com.devson.vedinsta.viewmodel.NotificationViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationViewModel: NotificationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel
        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        setupBottomNavigation()
        setupNotificationIcon()

        // Load the home fragment by default
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_downloads -> {
                    loadFragment(DownloadsFragment())
                    true
                }
                R.id.nav_favorites -> {
                    loadFragment(FavoritesFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun setupNotificationIcon() {
        // Observe unread notification count and update badge
        notificationViewModel.unreadCount.observe(this) { count ->
            if (count > 0) {
                binding.notificationBadge.visibility = View.VISIBLE
                binding.notificationBadge.text = if (count > 9) "9+" else count.toString()
            } else {
                binding.notificationBadge.visibility = View.GONE
            }
        }

        // Find the FrameLayout parent of iv_notification and set click listener
        val notificationContainer = binding.ivNotification.parent as View
        notificationContainer.setOnClickListener {
            // Mark all as read when opening notifications
            notificationViewModel.markAllAsRead()

            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

package com.devson.vedinsta

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.devson.vedinsta.databinding.ActivityMainBinding
import com.devson.vedinsta.ui.FavoritesFragment
import com.devson.vedinsta.ui.HomeFragment
import com.devson.vedinsta.ui.ProfileFragment
import com.devson.vedinsta.ui.SettingsFragment
import com.devson.vedinsta.viewmodel.NotificationViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationViewModel: NotificationViewModel
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        setupBottomNavigation()
        setupNotificationIcon()
        setupGridSizeIcon()

        // Load the home fragment by default
        if (savedInstanceState == null) {
            val homeFragment = HomeFragment()
            currentFragment = homeFragment
            loadFragment(HomeFragment())
            updateToolbarForPage("Home")
        }
    }

    private fun setupGridSizeIcon() {
        binding.gridSizeContainer.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (fragment is HomeFragment && fragment.isAdded) {
                fragment.showColumnSizeDialog()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val fragment = HomeFragment()
                    currentFragment = fragment
                    loadFragment(fragment)
                    updateToolbarForPage("Home")
                    true
                }
                R.id.nav_profile -> {
                    val fragment = ProfileFragment()
                    currentFragment = fragment // UPDATE
                    loadFragment(fragment)
                    updateToolbarForPage("Profile")
                    true
                }
                R.id.nav_favorites -> {
                    val fragment = FavoritesFragment()
                    currentFragment = fragment // UPDATE
                    loadFragment(fragment)
                    updateToolbarForPage("Favorites")
                    true
                }
                R.id.nav_settings -> {
                    val fragment = SettingsFragment()
                    currentFragment = fragment // UPDATE
                    loadFragment(fragment)
                    updateToolbarForPage("Settings")
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun updateToolbarForPage(pageName: String) {
        when (pageName) {
            "Home" -> {
                binding.toolbarTitle.text = "VedInsta"
                binding.toolbarTitle.textSize = 24f
                binding.notificationContainer.visibility = View.VISIBLE
                binding.gridSizeContainer.visibility = View.VISIBLE // ADD THIS
            }
            else -> {
                binding.toolbarTitle.text = pageName
                binding.toolbarTitle.textSize = 20f
                binding.notificationContainer.visibility = View.GONE
                binding.gridSizeContainer.visibility = View.GONE // ADD THIS
            }
        }
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

        // Set click listener for notification
        binding.notificationContainer.setOnClickListener {
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
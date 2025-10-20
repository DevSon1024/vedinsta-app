// src/main/java/com/devson/vedinsta/MainActivity.kt
package com.devson.vedinsta

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.devson.vedinsta.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomBar()
    }

    private fun setupBottomBar() {
        binding.bottomAppBar.replaceMenu(R.menu.bottom_app_bar_menu)
        binding.bottomAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.fabDownload.setOnClickListener {
            handleDownloadFabClick()
        }
    }

    private fun handleDownloadFabClick() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val clipData = clipboard.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(this, "No URL found in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = clipData.getItemAt(0).text.toString()
        if (!url.contains("instagram.com")) {
            Toast.makeText(this, "No Instagram URL in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        fetchMediaFromUrl(url)
    }

    private fun fetchMediaFromUrl(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val py = Python.getInstance()
            val pyModule = py.getModule("insta_downloader")
            val resultJson = pyModule.callAttr("get_media_urls", url).toString()

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                val intent = Intent(this@MainActivity, DownloadActivity::class.java).apply {
                    putExtra("RESULT_JSON", resultJson)
                }
                startActivity(intent)
            }
        }
    }
}
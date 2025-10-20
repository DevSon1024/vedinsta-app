// src/main/java/com/devson/vedinsta/SettingsActivity.kt
package com.devson.vedinsta

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.devson.vedinsta.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    // Launcher for Image folder picker
    private val imageFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    takePersistablePermissions(uri)
                    settingsManager.imageDirectoryUri = uri.toString()
                    updatePathLabels()
                    Toast.makeText(this, "Image location set", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Launcher for Video folder picker
    private val videoFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    takePersistablePermissions(uri)
                    settingsManager.videoDirectoryUri = uri.toString()
                    updatePathLabels()
                    Toast.makeText(this, "Video location set", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        settingsManager = SettingsManager(this)
        updatePathLabels()

        binding.btnPickImageFolder.setOnClickListener {
            openFolderPicker(imageFolderPickerLauncher)
        }

        binding.btnPickVideoFolder.setOnClickListener {
            openFolderPicker(videoFolderPickerLauncher)
        }
    }

    private fun openFolderPicker(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        launcher.launch(intent)
    }

    private fun takePersistablePermissions(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    private fun updatePathLabels() {
        binding.tvImagePath.text = settingsManager.getImagePathLabel()
        binding.tvVideoPath.text = settingsManager.getVideoPathLabel()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
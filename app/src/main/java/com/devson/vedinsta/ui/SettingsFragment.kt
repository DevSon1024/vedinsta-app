package com.devson.vedinsta.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.Coil
import com.devson.vedinsta.SettingsManager
import com.devson.vedinsta.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    private val imageFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    takePersistablePermissions(uri)
                    settingsManager.imageDirectoryUri = uri.toString()
                    updatePathLabels()
                    Toast.makeText(requireContext(), "Image location set", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val videoFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    takePersistablePermissions(uri)
                    settingsManager.videoDirectoryUri = uri.toString()
                    updatePathLabels()
                    Toast.makeText(requireContext(), "Video location set", Toast.LENGTH_SHORT).show()
                }
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsManager = SettingsManager(requireContext())
        setupClickListeners()
        updatePathLabels()
    }

    private fun setupClickListeners() {
        binding.btnPickImageFolder.setOnClickListener {
            openFolderPicker(imageFolderPickerLauncher)
        }
        binding.btnPickVideoFolder.setOnClickListener {
            openFolderPicker(videoFolderPickerLauncher)
        }
        binding.llStorageSettings.setOnClickListener {
            toggleStorageSection()
        }

        binding.llClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }

        // NEW: Notification / Link Behavior Setting
        binding.llNotificationSettings.setOnClickListener {
            showLinkActionDialog()
        }

        binding.llPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        binding.llAbout.setOnClickListener {
            val intent = Intent(requireContext(), com.devson.vedinsta.AboutActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showLinkActionDialog() {
        val options = arrayOf(
            "Request Action (Default)",
            "Download All Immediately",
            "Open Media Selection"
        )
        // Map current setting to index
        val currentSelection = when(settingsManager.defaultLinkAction) {
            SettingsManager.ACTION_ASK_EVERY_TIME -> 0
            SettingsManager.ACTION_DOWNLOAD_ALL -> 1
            SettingsManager.ACTION_OPEN_SELECTION -> 2
            else -> 0
        }

        AlertDialog.Builder(requireContext())
            .setTitle("When sharing a link:")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                val newAction = when(which) {
                    0 -> SettingsManager.ACTION_ASK_EVERY_TIME
                    1 -> SettingsManager.ACTION_DOWNLOAD_ALL
                    2 -> SettingsManager.ACTION_OPEN_SELECTION
                    else -> SettingsManager.ACTION_ASK_EVERY_TIME
                }
                settingsManager.defaultLinkAction = newAction
                updatePathLabels() // Refresh label
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleStorageSection() {
        if (binding.storageDetails.visibility == View.VISIBLE) {
            binding.storageDetails.visibility = View.GONE
            binding.ivStorageArrow.rotation = 0f
        } else {
            binding.storageDetails.visibility = View.VISIBLE
            binding.ivStorageArrow.rotation = 90f
        }
    }

    private fun openFolderPicker(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error launching folder picker", e)
            Toast.makeText(requireContext(), "Cannot open folder picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePersistablePermissions(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            Log.e("SettingsFragment", "Failed to take persistable permissions", e)
        }
    }

    private fun updatePathLabels() {
        binding.tvImagePath.text = settingsManager.getImagePathLabel()
        binding.tvVideoPath.text = settingsManager.getVideoPathLabel()
        // New: Update link action status text
        binding.tvLinkActionStatus.text = settingsManager.getDefaultActionLabel()
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://github.com/DevSon1024/vedinsta-app/blob/main/PRIVACY_POLICY.md")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearCacheConfirmation() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("This will remove temporary files. Downloads remain safe. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearApplicationCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearApplicationCache() {
        if (!isAdded) return
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir?.let { deleteDir(it) }
                context.externalCacheDir?.let { deleteDir(it) }
                val imageLoader = Coil.imageLoader(context)
                withContext(Dispatchers.Main) {
                    imageLoader.memoryCache?.clear()
                }
                imageLoader.diskCache?.clear()
                withContext(Dispatchers.Main) {
                    if (isAdded) Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Error clearing cache", e)
            }
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list() ?: return false
            for (i in children.indices) {
                deleteDir(File(dir, children[i]))
            }
        }
        return dir?.delete() ?: false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
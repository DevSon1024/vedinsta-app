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

    // ... (Launchers remain the same) ...
    // Launcher for Image folder picker
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

    // Launcher for Video folder picker
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
        // Storage Section
        binding.btnPickImageFolder.setOnClickListener {
            openFolderPicker(imageFolderPickerLauncher)
        }
        binding.btnPickVideoFolder.setOnClickListener {
            openFolderPicker(videoFolderPickerLauncher)
        }
        binding.llStorageSettings.setOnClickListener {
            toggleStorageSection()
        }

        // --- Clear Cache ---
        binding.llClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }
        // --- ---

        // Privacy Policy
        binding.llPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        // About - Changed to launch AboutActivity
        binding.llAbout.setOnClickListener {
            // Use the standard way to launch AboutActivity
            val intent = Intent(requireContext(), com.devson.vedinsta.AboutActivity::class.java)
            startActivity(intent)
        }
    }

    // ... (toggleStorageSection, openFolderPicker, takePersistablePermissions, updatePathLabels, openPrivacyPolicy remain the same) ...
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
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Optionally add initial URI if needed
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, ...)
        }
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
            Log.d("SettingsFragment", "Persistable permissions taken for URI: $uri")
        } catch (e: SecurityException) {
            Log.e("SettingsFragment", "Failed to take persistable permissions for $uri", e)
            Toast.makeText(requireContext(), "Failed to grant access. Please try again.", Toast.LENGTH_LONG).show()
        }
    }


    private fun updatePathLabels() {
        binding.tvImagePath.text = settingsManager.getImagePathLabel()
        binding.tvVideoPath.text = settingsManager.getVideoPathLabel()
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://github.com/DevSon1024/vedinsta-app/blob/main/PRIVACY_POLICY.md") // Ensure this URL is correct
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No browser app found to open link", Toast.LENGTH_SHORT).show()
        }
    }


    // Removed showAboutDialog, using AboutActivity now

    // --- Clear Cache Implementation ---
    private fun showClearCacheConfirmation() {
        // Ensure fragment is added before showing dialog
        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("This will remove temporary files and cached images. Downloaded media will not be affected. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearApplicationCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearApplicationCache() {
        // Check fragment state again before launching coroutine
        if (!isAdded) return

        Log.d("SettingsFragment", "Clearing application cache...")
        val context = requireContext().applicationContext // Use application context safely

        // Use lifecycleScope tied to the fragment's view lifecycle
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var cacheCleared = false
            var coilCleared = false
            try {
                // Clear app's internal cache directory
                context.cacheDir?.let { deleteDir(it) }
                // Clear app's external cache directory (if used)
                context.externalCacheDir?.let { deleteDir(it) }
                cacheCleared = true
                Log.d("SettingsFragment", "System cache directories cleared.")

                // Clear Coil's cache
                val imageLoader = Coil.imageLoader(context)
                // These might need Dispatchers.Main if they interact with UI thread internally
                withContext(Dispatchers.Main) {
                    imageLoader.memoryCache?.clear() // Clear memory cache (Main thread safe)
                }
                imageLoader.diskCache?.clear()   // Clear disk cache (runs IO, already on IO thread)
                coilCleared = true
                Log.d("SettingsFragment", "Coil memory and disk cache cleared.")

                withContext(Dispatchers.Main) {
                    // Check if fragment is still added before showing Toast
                    if (isAdded) {
                        Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Error clearing cache", e)
                withContext(Dispatchers.Main) {
                    // Check if fragment is still added before showing Toast
                    if (isAdded) {
                        val message = when {
                            !cacheCleared -> "Failed to clear system cache"
                            !coilCleared -> "Failed to clear image cache"
                            else -> "An error occurred while clearing cache"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Recursive function to delete directory contents
    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list() ?: return false // Check if list() returns null
            for (i in children.indices) {
                // Check for cancellation before deleting each child
                // No easy way to check lifecycleScope cancellation here directly
                // Rely on the outer scope being cancelled if the fragment is destroyed
                val success = deleteDir(File(dir, children[i]))
                if (!success) {
                    Log.w("SettingsFragment", "Failed to delete file/subdir: ${children[i]}")
                    // Continue trying to delete others
                }
            }
        }
        // The directory is now empty or it's a file
        return dir?.delete() ?: false
    }
    // --- ---

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important for preventing memory leaks
    }
}
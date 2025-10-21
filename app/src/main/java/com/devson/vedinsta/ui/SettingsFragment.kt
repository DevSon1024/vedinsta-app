package com.devson.vedinsta.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.devson.vedinsta.SettingsManager
import com.devson.vedinsta.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

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

        // Privacy Policy
        binding.llPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        // About
        binding.llAbout.setOnClickListener {
            showAboutDialog()
        }
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
        launcher.launch(intent)
    }

    private fun takePersistablePermissions(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    private fun updatePathLabels() {
        binding.tvImagePath.text = settingsManager.getImagePathLabel()
        binding.tvVideoPath.text = settingsManager.getVideoPathLabel()
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

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("About VedInsta")
            .setMessage("VedInsta v1.0.0\n\nA powerful Instagram downloader app that helps you save photos and videos from Instagram posts, stories, and reels.\n\nDeveloper: DevSon1024\nGitHub: github.com/DevSon1024/vedinsta-app")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Visit GitHub") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DevSon1024/vedinsta-app"))
                startActivity(intent)
            }
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
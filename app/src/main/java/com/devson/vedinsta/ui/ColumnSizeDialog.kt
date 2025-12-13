package com.devson.vedinsta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.devson.vedinsta.SettingsManager
import com.devson.vedinsta.databinding.DialogColumnSizeBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ColumnSizeDialog(
    private val currentColumns: Int,
    private val onColumnSizeChanged: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogColumnSizeBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private var selectedColumns = currentColumns

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogColumnSizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsManager = SettingsManager(requireContext())
        setupViews()
    }

    private fun setupViews() {
        // Initialize with current value
        selectedColumns = currentColumns
        binding.seekBar.progress = currentColumns - 1 // SeekBar is 0-based
        binding.tvColumnCount.text = currentColumns.toString()
        updatePreviewGrid(currentColumns)

        // SeekBar listener for live preview
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    selectedColumns = progress + 1 // Convert to 1-6 range
                    binding.tvColumnCount.text = selectedColumns.toString()
                    updatePreviewGrid(selectedColumns)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Apply button
        binding.btnApply.setOnClickListener {
            // Save preference
            settingsManager.gridColumnCount = selectedColumns
            // Notify parent
            onColumnSizeChanged(selectedColumns)
            dismiss()
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updatePreviewGrid(columns: Int) {
        // Update visibility of preview items based on column count
        binding.previewItem1.visibility = if (columns >= 1) View.VISIBLE else View.GONE
        binding.previewItem2.visibility = if (columns >= 2) View.VISIBLE else View.GONE
        binding.previewItem3.visibility = if (columns >= 3) View.VISIBLE else View.GONE
        binding.previewItem4.visibility = if (columns >= 4) View.VISIBLE else View.GONE
        binding.previewItem5.visibility = if (columns >= 5) View.VISIBLE else View.GONE
        binding.previewItem6.visibility = if (columns >= 6) View.VISIBLE else View.GONE

        // Update description text
        val description = when (columns) {
            1 -> "Single column - Large detailed view"
            2 -> "Two columns - Comfortable viewing"
            3 -> "Three columns - Default balanced view"
            4 -> "Four columns - Compact grid"
            5 -> "Five columns - Dense layout"
            6 -> "Six columns - Maximum density"
            else -> ""
        }
        binding.tvDescription.text = description
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ColumnSizeDialog"
    }
}
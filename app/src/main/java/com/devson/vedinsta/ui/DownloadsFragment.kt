package com.devson.vedinsta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView // Import TextView
import androidx.fragment.app.Fragment
import com.devson.vedinsta.databinding.FragmentFavoritesBinding // Keeping this as it was in the original file

class DownloadsFragment : Fragment() {

    // Assuming you have a layout named 'fragment_downloads.xml'
    // If you are using FragmentFavoritesBinding intentionally, keep it.
    // Otherwise, change this to FragmentDownloadsBinding?
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the correct layout for DownloadsFragment if it exists
        // Otherwise, keep FragmentFavoritesBinding if that's intended
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the RecyclerView and show a "Coming Soon" message in the empty state
        setupComingSoonState()
    }

    private fun setupComingSoonState() {
        // Assuming your layout has an empty_state LinearLayout containing TextViews
        // Modify these IDs if your actual layout (fragment_downloads.xml or fragment_favorites.xml) is different

        // Hide the RecyclerView if it exists in the layout
        // binding.rvDownloads.visibility = View.GONE // Example if using FragmentDownloadsBinding

        // Make the empty state visible
        binding.emptyState.visibility = View.VISIBLE

        // Find the TextViews within the empty_state LinearLayout by ID
        // You might need to adjust these IDs based on your actual XML layout
        val titleTextView = binding.emptyState.findViewById<TextView>(androidx.appcompat.R.id.title) // Example standard ID
        val messageTextView = binding.emptyState.findViewById<TextView>(androidx.core.R.id.text) // Example standard ID

        // Update the text
        titleTextView?.text = "Coming Soon!"
        messageTextView?.text = "This feature is currently under development."

        // Optionally hide the icon if it doesn't fit the "Coming Soon" message
        // val iconImageView = binding.emptyState.findViewById<ImageView>(R.id.your_icon_id)
        // iconImageView?.visibility = View.GONE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
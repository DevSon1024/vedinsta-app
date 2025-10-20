package com.devson.vedinsta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class FavoritesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val textView = TextView(context).apply {
            text = "Favorites feature coming soon!"
            textSize = 18f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        return textView
    }
}

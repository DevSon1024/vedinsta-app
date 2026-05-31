package com.devson.vedinsta.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.devson.vedinsta.InstagramComposeActivity
import com.devson.vedinsta.adapters.SessionAdapter
import com.devson.vedinsta.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionAdapter: SessionAdapter

    // This would be replaced with actual session data from SharedPreferences or a database
    private val sessionList = mutableListOf("default_session", "user_abc")
    private var activeSession = "default_session"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        sessionAdapter = SessionAdapter(sessionList, activeSession) { sessionUsername ->
            // Handle session selection
            activeSession = sessionUsername
            sessionAdapter.setActiveSession(activeSession)

            // Show success message
            binding.tvSuccessMessage.text = "Session '$activeSession' is now active."
            binding.tvSuccessMessage.visibility = View.VISIBLE

            // Here you would save the active session to SharedPreferences
            // settingsManager.activeSession = activeSession
        }

        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sessionAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnAddNewSession.setOnClickListener {
            val intent = Intent(requireContext(), InstagramComposeActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
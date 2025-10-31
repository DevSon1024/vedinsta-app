package com.devson.vedinsta.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.databinding.ItemSessionBinding

class SessionAdapter(
    private val sessions: List<String>,
    private var activeSession: String,
    private val onSessionSelect: (String) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    fun setActiveSession(username: String) {
        activeSession = username
        notifyDataSetChanged() // Refresh the list to show new active session
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class SessionViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(username: String) {
            binding.tvUsername.text = username

            if (username == activeSession) {
                binding.tvActiveStatus.visibility = View.VISIBLE
                binding.btnSelectSession.isEnabled = false
                binding.btnSelectSession.text = "Active"
            } else {
                binding.tvActiveStatus.visibility = View.GONE
                binding.btnSelectSession.isEnabled = true
                binding.btnSelectSession.text = "Select"
            }

            binding.btnSelectSession.setOnClickListener {
                onSessionSelect(username)
            }
        }
    }
}
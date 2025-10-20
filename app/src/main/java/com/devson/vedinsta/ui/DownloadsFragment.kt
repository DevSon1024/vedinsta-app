package com.devson.vedinsta.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.devson.vedinsta.databinding.FragmentDownloadsBinding
import com.devson.vedinsta.viewmodel.DownloadsViewModel

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var downloadsAdapter: DownloadStatusAdapter
    private lateinit var viewModel: DownloadsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DownloadsViewModel::class.java]

        setupRecyclerView()
        observeDownloads()
    }

    private fun setupRecyclerView() {
        downloadsAdapter = DownloadStatusAdapter(
            onItemClick = { downloadItem ->
                // Handle click if needed
            },
            onRetryClick = { downloadItem ->
                // Handle retry if needed
                viewModel.retryDownload(downloadItem)
            }
        )

        binding.rvDownloads.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadsAdapter
        }
    }

    private fun observeDownloads() {
        viewModel.allDownloads.observe(viewLifecycleOwner) { downloads ->
            downloadsAdapter.submitList(downloads)
            updateEmptyState(downloads.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvDownloads.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvDownloads.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.devson.vedinsta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.ui.DownloadItem
import com.devson.vedinsta.ui.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val _allDownloads = MutableLiveData<List<DownloadItem>>()
    val allDownloads: LiveData<List<DownloadItem>> = _allDownloads

    private val downloadsList = mutableListOf<DownloadItem>()

    init {
        // Initialize with some sample data for demonstration
        // In a real app, this would come from a database or download manager
        loadSampleDownloads()
    }

    private fun loadSampleDownloads() {
        val sampleDownloads = listOf(
            DownloadItem(
                id = "1",
                filename = "instagram_reel_abc123.mp4",
                status = DownloadStatus.COMPLETED,
                progress = 100
            ),
            DownloadItem(
                id = "2",
                filename = "instagram_post_def456.jpg",
                status = DownloadStatus.DOWNLOADING,
                progress = 75
            ),
            DownloadItem(
                id = "3",
                filename = "instagram_story_ghi789.jpg",
                status = DownloadStatus.FAILED,
                progress = 0
            ),
            DownloadItem(
                id = "4",
                filename = "instagram_carousel_jkl012.jpg",
                status = DownloadStatus.PENDING,
                progress = 0
            )
        )

        downloadsList.addAll(sampleDownloads)
        _allDownloads.value = downloadsList.toList()

        // Simulate download progress updates
        startProgressUpdates()
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second

                var hasChanges = false
                downloadsList.forEachIndexed { index, item ->
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            if (item.progress < 100) {
                                downloadsList[index] = item.copy(progress = item.progress + 5)
                                hasChanges = true
                            } else {
                                downloadsList[index] = item.copy(
                                    status = DownloadStatus.COMPLETED,
                                    progress = 100
                                )
                                hasChanges = true
                            }
                        }
                        DownloadStatus.PENDING -> {
                            // Simulate starting download after some time
                            if (Math.random() > 0.7) {
                                downloadsList[index] = item.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = 5
                                )
                                hasChanges = true
                            }
                        }
                        else -> {
                            // No updates needed for completed/failed/paused
                        }
                    }
                }

                if (hasChanges) {
                    _allDownloads.value = downloadsList.toList()
                }
            }
        }
    }

    fun addDownload(filename: String, url: String) {
        val newDownload = DownloadItem(
            id = System.currentTimeMillis().toString(),
            filename = filename,
            status = DownloadStatus.PENDING,
            url = url
        )

        downloadsList.add(0, newDownload) // Add to top
        _allDownloads.value = downloadsList.toList()
    }

    fun retryDownload(downloadItem: DownloadItem) {
        val index = downloadsList.indexOfFirst { it.id == downloadItem.id }
        if (index != -1) {
            downloadsList[index] = downloadItem.copy(
                status = DownloadStatus.PENDING,
                progress = 0
            )
            _allDownloads.value = downloadsList.toList()
        }
    }

    fun pauseDownload(downloadId: String) {
        val index = downloadsList.indexOfFirst { it.id == downloadId }
        if (index != -1 && downloadsList[index].status == DownloadStatus.DOWNLOADING) {
            downloadsList[index] = downloadsList[index].copy(status = DownloadStatus.PAUSED)
            _allDownloads.value = downloadsList.toList()
        }
    }

    fun resumeDownload(downloadId: String) {
        val index = downloadsList.indexOfFirst { it.id == downloadId }
        if (index != -1 && downloadsList[index].status == DownloadStatus.PAUSED) {
            downloadsList[index] = downloadsList[index].copy(status = DownloadStatus.DOWNLOADING)
            _allDownloads.value = downloadsList.toList()
        }
    }

    fun cancelDownload(downloadId: String) {
        val index = downloadsList.indexOfFirst { it.id == downloadId }
        if (index != -1) {
            downloadsList.removeAt(index)
            _allDownloads.value = downloadsList.toList()
        }
    }
}

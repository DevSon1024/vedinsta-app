package com.devson.vedinsta.adapters

import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import com.devson.vedinsta.R
import com.devson.vedinsta.databinding.ItemMediaCarouselBinding
import java.io.File
import java.util.concurrent.TimeUnit

class MediaCarouselAdapter(
    private val mediaFiles: List<File>,
    private val onMediaClick: () -> Unit,
    private val onVideoPlayPause: (Boolean) -> Unit
) : RecyclerView.Adapter<MediaCarouselAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaCarouselBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaFiles[position])
    }

    override fun getItemCount(): Int = mediaFiles.size

    inner class MediaViewHolder(
        private val binding: ItemMediaCarouselBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isPlaying = false
        private var videoDuration = 0
        private val updateHandler = Handler(Looper.getMainLooper())
        private val updateRunnable = object : Runnable {
            override fun run() {
                updateVideoProgress()
                if (isPlaying) {
                    updateHandler.postDelayed(this, 1000)
                }
            }
        }

        fun bind(mediaFile: File) {
            val isVideo = mediaFile.extension.lowercase() in listOf("mp4", "mov", "avi")

            if (isVideo) {
                setupVideoView(mediaFile)
            } else {
                setupImageView(mediaFile)
            }
        }

        private fun setupVideoView(videoFile: File) {
            binding.ivMedia.visibility = View.GONE
            binding.rlVideoContainer.visibility = View.VISIBLE
            binding.ivPlayButton.visibility = View.VISIBLE
            binding.llVideoControls.visibility = View.GONE

            val uri = Uri.fromFile(videoFile)
            binding.videoView.setVideoURI(uri)

            // Load video thumbnail for preview
            loadVideoThumbnailForPreview(videoFile)

            // Setup video prepared listener
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                videoDuration = mediaPlayer.duration
                binding.seekBar.max = videoDuration
                binding.tvDuration.text = formatDuration(videoDuration)

                // Don't auto-start the video
                mediaPlayer.setOnVideoSizeChangedListener { _, _, _ ->
                    // Video is ready but paused
                }
            }

            // Handle main play button click
            binding.ivPlayButton.setOnClickListener {
                startVideo()
            }

            // Handle play/pause button in controls
            binding.btnPlayPause.setOnClickListener {
                togglePlayPause()
            }

            // Handle seekbar changes
            binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.videoView.seekTo(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Handle video completion
            binding.videoView.setOnCompletionListener {
                stopVideo()
            }

            // Handle video error
            binding.videoView.setOnErrorListener { _, _, _ ->
                // Reset to initial state on error
                stopVideo()
                true
            }
        }

        private fun setupImageView(imageFile: File) {
            binding.rlVideoContainer.visibility = View.GONE
            binding.ivMedia.visibility = View.VISIBLE

            binding.ivMedia.load(imageFile) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(300)
                size(Size.ORIGINAL)
            }

            binding.ivMedia.setOnClickListener {
                onMediaClick()
            }
        }

        private fun loadVideoThumbnailForPreview(videoFile: File) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0, // First frame
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        400,
                        400
                    )
                } else {
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                retriever.release()

                if (bitmap != null) {
                    // Modern way to set background drawable
                    val bitmapDrawable = BitmapDrawable(binding.root.context.resources, bitmap)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        binding.videoView.background = bitmapDrawable
                    } else {
                        @Suppress("DEPRECATION")
                        binding.videoView.setBackgroundDrawable(bitmapDrawable)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Set a default background color for videos
                binding.videoView.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
            }
        }

        private fun startVideo() {
            binding.videoView.start()
            binding.ivPlayButton.visibility = View.GONE
            binding.llVideoControls.visibility = View.VISIBLE
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause_white)
            isPlaying = true
            startProgressUpdate()
            onVideoPlayPause(true)
        }

        private fun togglePlayPause() {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow_white)
                isPlaying = false
                stopProgressUpdate()
                onVideoPlayPause(false)
            } else {
                binding.videoView.start()
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause_white)
                isPlaying = true
                startProgressUpdate()
                onVideoPlayPause(true)
            }
        }

        private fun stopVideo() {
            binding.videoView.pause()
            binding.videoView.seekTo(0)
            binding.ivPlayButton.visibility = View.VISIBLE
            binding.llVideoControls.visibility = View.GONE
            binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow_white)
            binding.seekBar.progress = 0
            isPlaying = false
            stopProgressUpdate()
            onVideoPlayPause(false)
        }

        private fun startProgressUpdate() {
            updateHandler.post(updateRunnable)
        }

        private fun stopProgressUpdate() {
            updateHandler.removeCallbacks(updateRunnable)
        }

        private fun updateVideoProgress() {
            if (binding.videoView.isPlaying) {
                val currentPosition = binding.videoView.currentPosition
                binding.seekBar.progress = currentPosition

                val remaining = videoDuration - currentPosition
                binding.tvDuration.text = formatDuration(remaining)
            }
        }

        private fun formatDuration(milliseconds: Int): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
            val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
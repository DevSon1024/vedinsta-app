package com.devson.vedinsta.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.media.MediaScannerConnection
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

sealed class WhatsAppState {
    object Loading : WhatsAppState()
    object PermissionRequired : WhatsAppState()
    data class Success(val statuses: List<DocumentFile>) : WhatsAppState()
    data class Error(val message: String) : WhatsAppState()
}

class WhatsAppViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<WhatsAppState>(WhatsAppState.Loading)
    val state: StateFlow<WhatsAppState> = _state.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("whats_app_saver_prefs", Context.MODE_PRIVATE)

    private val _savedStatuses = MutableStateFlow<Set<String>>(emptySet())
    val savedStatuses: StateFlow<Set<String>> = _savedStatuses.asStateFlow()

    private var contentObserver: ContentObserver? = null
    private var observerJob: kotlinx.coroutines.Job? = null

    private val _isSevenDaySaverEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("seven_day_saver_enabled", false)
    )
    val isSevenDaySaverEnabled: StateFlow<Boolean> = _isSevenDaySaverEnabled.asStateFlow()

    private val _preservedStatuses = MutableStateFlow<List<File>>(emptyList())
    val preservedStatuses: StateFlow<List<File>> = _preservedStatuses.asStateFlow()

    init {
        pruneOldSavedStatusRecords()
        cleanupPreservedStatusesOnStartup()
        enqueueStatusCleanupWorker(application)
    }

    fun checkPermission(context: Context) {
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        var foundUri: Uri? = null

        for (permission in persistedPermissions) {
            val uriString = permission.uri.toString()
            if (permission.isReadPermission && (uriString.contains(".Statuses") || uriString.contains("com.whatsapp"))) {
                try {
                    val document = DocumentFile.fromTreeUri(context, permission.uri)
                    if (document != null && document.exists() && document.canRead()) {
                        foundUri = permission.uri
                        break
                    }
                } catch (e: Exception) {
                    // Stale permission, ignore
                }
            }
        }

        if (foundUri != null) {
            loadStatuses(context, foundUri)
        } else {
            _state.value = WhatsAppState.PermissionRequired
        }
    }

    fun loadStatuses(context: Context, treeUri: Uri) {
        startObservingStatuses(context.applicationContext, treeUri)
        if (_state.value !is WhatsAppState.Success) {
            _state.value = WhatsAppState.Loading
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, treeUri)
                if (directory == null || !directory.exists()) {
                    _state.value = WhatsAppState.Error("Selected directory does not exist or cannot be accessed.")
                    return@launch
                }

                val files = directory.listFiles()
                val filteredFiles = files.filter { file ->
                    val name = file.name ?: ""
                    val isNotNoMedia = !name.equals(".nomedia", ignoreCase = true)
                    val isSupportedExtension = name.endsWith(".jpg", ignoreCase = true) ||
                            name.endsWith(".jpeg", ignoreCase = true) ||
                            name.endsWith(".png", ignoreCase = true) ||
                            name.endsWith(".mp4", ignoreCase = true)
                    isNotNoMedia && isSupportedExtension
                }.sortedByDescending { it.lastModified() }

                _state.value = WhatsAppState.Success(filteredFiles)
                autoPreserveNewStatuses(context, filteredFiles)
            } catch (e: Exception) {
                _state.value = WhatsAppState.Error(e.message ?: "Failed to load statuses.")
            }
        }
    }

    fun saveStatus(context: Context, statusFile: DocumentFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val inputStream: InputStream? = resolver.openInputStream(statusFile.uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot open source status file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFolder = File(downloadsDir, "VedInstaStatusSaver")
                if (!targetFolder.exists()) {
                    val created = targetFolder.mkdirs()
                    if (!created && !targetFolder.exists()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to create target directory", Toast.LENGTH_SHORT).show()
                        }
                        inputStream.close()
                        return@launch
                    }
                }

                val fileName = statusFile.name ?: "status_${System.currentTimeMillis()}"
                val targetFile = File(targetFolder, fileName)

                var finalFile = targetFile
                if (finalFile.exists()) {
                    val nameWithoutExtension = fileName.substringBeforeLast(".")
                    val extension = fileName.substringAfterLast(".", "")
                    val extStr = if (extension.isNotEmpty()) ".$extension" else ""
                    var count = 1
                    while (finalFile.exists()) {
                        finalFile = File(targetFolder, "${nameWithoutExtension}_$count$extStr")
                        count++
                    }
                }

                val outputStream = FileOutputStream(finalFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                markStatusAsSaved(fileName)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    null
                ) { _, _ ->
                    // Indexing finished
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Status saved to Downloads/VedInstaStatusSaver", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun saveAudioFromVideo(context: Context, statusFile: DocumentFile) {
        viewModelScope.launch(Dispatchers.IO) {
            var extractor: android.media.MediaExtractor? = null
            var muxer: android.media.MediaMuxer? = null
            try {
                val resolver = context.contentResolver
                val pfd = resolver.openFileDescriptor(statusFile.uri, "r")
                if (pfd == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot open video source descriptor", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                extractor = android.media.MediaExtractor()
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()

                var audioTrackIndex = -1
                var audioFormat: android.media.MediaFormat? = null
                val numTracks = extractor.trackCount
                for (i in 0 until numTracks) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }

                if (audioTrackIndex == -1 || audioFormat == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No audio track found in the video", Toast.LENGTH_SHORT).show()
                    }
                    extractor.release()
                    return@launch
                }

                extractor.selectTrack(audioTrackIndex)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val parentFolder = File(downloadsDir, "VedInstaStatusSaver")
                val targetFolder = File(parentFolder, "audio")
                if (!targetFolder.exists()) {
                    val created = targetFolder.mkdirs()
                    if (!created && !targetFolder.exists()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to create audio directory", Toast.LENGTH_SHORT).show()
                        }
                        extractor.release()
                        return@launch
                    }
                }

                val sourceName = statusFile.name ?: "status_${System.currentTimeMillis()}"
                val baseName = sourceName.substringBeforeLast(".")
                val mimeType = audioFormat.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                val extension = if (mimeType.contains("ogg")) "ogg" else "m4a"
                val targetFile = File(targetFolder, "$baseName.$extension")

                var finalFile = targetFile
                if (finalFile.exists()) {
                    var count = 1
                    while (finalFile.exists()) {
                        finalFile = File(targetFolder, "${baseName}_$count.$extension")
                        count++
                    }
                }

                muxer = android.media.MediaMuxer(finalFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val writeTrackIndex = muxer.addTrack(audioFormat)
                muxer.start()

                val maxBufferSize = if (audioFormat.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    audioFormat.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    64 * 1024
                }
                val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
                val bufferInfo = android.media.MediaCodec.BufferInfo()

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        break
                    }
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                extractor.release()

                markStatusAsSaved(sourceName)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    null
                ) { _, _ -> }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Audio Saved to Downloads/VedInstaStatusSaver/audio", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    extractor?.release()
                } catch (ex: Exception) {}
                try {
                    muxer?.release()
                } catch (ex: Exception) {}
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Audio Saving Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startObservingStatuses(context: Context, treeUri: Uri) {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                observerJob?.cancel()
                observerJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(1000)
                    loadStatuses(context, treeUri)
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(treeUri, true, contentObserver!!)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun stopObservingStatuses(context: Context) {
        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {}
            contentObserver = null
        }
    }

    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        return "$year-$day"
    }

    private fun pruneOldSavedStatusRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = getTodayDateString()
            val allPrefs = sharedPrefs.all
            val toRemove = mutableListOf<String>()
            val activeSaved = mutableSetOf<String>()

            for ((key, value) in allPrefs) {
                if (value is String) {
                    if (value != today) {
                        toRemove.add(key)
                    } else {
                        activeSaved.add(key)
                    }
                }
            }

            if (toRemove.isNotEmpty()) {
                val editor = sharedPrefs.edit()
                for (key in toRemove) {
                    editor.remove(key)
                }
                editor.apply()
            }

            _savedStatuses.value = activeSaved
        }
    }

    private fun markStatusAsSaved(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = getTodayDateString()
            sharedPrefs.edit().putString(fileName, today).apply()

            val current = _savedStatuses.value.toMutableSet()
            current.add(fileName)
            _savedStatuses.value = current
        }
    }

    fun saveStatuses(context: Context, statusFiles: List<DocumentFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            var savedCount = 0
            var failedCount = 0
            for (statusFile in statusFiles) {
                try {
                    val resolver = context.contentResolver
                    val inputStream = resolver.openInputStream(statusFile.uri) ?: continue

                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFolder = File(downloadsDir, "VedInstaStatusSaver")
                    if (!targetFolder.exists()) {
                        targetFolder.mkdirs()
                    }

                    val fileName = statusFile.name ?: "status_${System.currentTimeMillis()}"
                    val targetFile = File(targetFolder, fileName)
                    var finalFile = targetFile
                    if (finalFile.exists()) {
                        val nameWithoutExtension = fileName.substringBeforeLast(".")
                        val extension = fileName.substringAfterLast(".", "")
                        val extStr = if (extension.isNotEmpty()) ".$extension" else ""
                        var count = 1
                        while (finalFile.exists()) {
                            finalFile = File(targetFolder, "${nameWithoutExtension}_$count$extStr")
                            count++
                        }
                    }

                    val outputStream = FileOutputStream(finalFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    markStatusAsSaved(fileName)

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFile.absolutePath),
                        null
                    ) { _, _ -> }

                    savedCount++
                } catch (e: Exception) {
                    failedCount++
                }
            }
            withContext(Dispatchers.Main) {
                if (savedCount > 0) {
                    Toast.makeText(context, "Saved $savedCount statuses to Downloads/VedInstaStatusSaver", Toast.LENGTH_SHORT).show()
                }
                if (failedCount > 0) {
                    Toast.makeText(context, "Failed to save $failedCount statuses", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setSevenDaySaverEnabled(context: Context, enabled: Boolean) {
        _isSevenDaySaverEnabled.value = enabled
        sharedPrefs.edit().putBoolean("seven_day_saver_enabled", enabled).apply()
        if (enabled) {
            val currentState = _state.value
            if (currentState is WhatsAppState.Success) {
                viewModelScope.launch(Dispatchers.IO) {
                    autoPreserveNewStatuses(context, currentState.statuses)
                }
            }
            enqueueStatusCleanupWorker(context)
        }
    }

    fun loadPreservedStatuses(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preserverDir = context.getExternalFilesDir("WAPreserver")
                val files = if (preserverDir != null && preserverDir.exists()) {
                    preserverDir.listFiles()?.filter { file ->
                        val name = file.name
                        name.endsWith(".jpg", ignoreCase = true) ||
                                name.endsWith(".jpeg", ignoreCase = true) ||
                                name.endsWith(".png", ignoreCase = true) ||
                                name.endsWith(".mp4", ignoreCase = true)
                    }?.sortedByDescending { it.lastModified() } ?: emptyList()
                } else {
                    emptyList()
                }
                _preservedStatuses.value = files
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun deletePreservedStatus(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        loadPreservedStatuses(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Status deleted", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun autoPreserveNewStatuses(context: Context, newStatuses: List<DocumentFile>) {
        if (!_isSevenDaySaverEnabled.value) return
        val preserverDir = context.getExternalFilesDir("WAPreserver") ?: return
        if (!preserverDir.exists()) {
            preserverDir.mkdirs()
        }
        val preservedNames = preserverDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        
        for (statusFile in newStatuses) {
            val fileName = statusFile.name ?: continue
            if (fileName in preservedNames) continue
            
            try {
                val resolver = context.contentResolver
                resolver.openInputStream(statusFile.uri)?.use { inputStream ->
                    val targetFile = File(preserverDir, fileName)
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                // Silently ignore copy failures
            }
        }
        loadPreservedStatuses(context)
    }

    private fun cleanupPreservedStatusesOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val preserverDir = context.getExternalFilesDir("WAPreserver") ?: return@launch
                if (preserverDir.exists()) {
                    val now = System.currentTimeMillis()
                    val threshold = 7L * 24L * 60L * 60L * 1000L // 7 days in ms
                    preserverDir.listFiles()?.forEach { file ->
                        if (now - file.lastModified() > threshold) {
                            file.delete()
                        }
                    }
                }
                loadPreservedStatuses(context)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun enqueueStatusCleanupWorker(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<com.devson.vedinsta.service.StatusCleanupWorker>(
                24, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "WAPreserverCleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopObservingStatuses(getApplication())
    }
}

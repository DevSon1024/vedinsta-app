package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.devson.vedinsta.model.MediaResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaFetcherRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Executes mo3.py using Chaquopy and returns the list of MediaResult objects.
     * Throws exception if Python execution fails or the script reports an error.
     */
    suspend fun fetchMedia(urlOrShortcode: String): List<MediaResult> = withContext(Dispatchers.IO) {
        try {
            // Ensure Python is initialized
            if (!Python.isStarted()) {
                throw Exception("Python is not started")
            }

            val python = Python.getInstance()
            val sys = python.getModule("sys")
            
            // Set the sys.argv so that mo3.py reads sys.argv[1] as the shortcode
            sys.put("argv", arrayOf("mo3.py", urlOrShortcode))
            
            // Change the Python process's working directory to context.filesDir
            // this ensures mo3.py finds "instagram_cookies.txt" under context.filesDir
            val os = python.getModule("os")
            os.callAttr("chdir", context.filesDir.absolutePath)
            
            // Setup stdout redirection to capture the printed JSON output
            val io = python.getModule("io")
            val stringIO = io.callAttr("StringIO")
            val originalStdout = sys.get("stdout")
            sys.put("stdout", stringIO)
            
            try {
                val builtins = python.getModule("builtins")
                
                // Define the Python execution script that reloads mo3 and runs the extraction logic
                val execCode = """
                    import sys, json, os, importlib
                    import mo3
                    
                    # Reload the module to force execution of clean logic with the new argv
                    importlib.reload(mo3)
                    
                    sc = sys.argv[1]
                    
                    # If a full URL is passed, extract the shortcode
                    if "instagram.com/" in sc:
                        for segment in ["/p/", "/reel/", "/reels/", "/tv/"]:
                            if segment in sc:
                                sc = sc.split(segment)[1].strip("/").split("/")[0].split("?")[0]
                                break
                    
                    # Call extract_instagram with the shortcode
                    results = mo3.extract_instagram(sc)
                    
                    # Print results to stdout as JSON so Kotlin can capture it
                    print(json.dumps(results))
                """.trimIndent()
                
                builtins.callAttr("exec", execCode)
                
                // Read captured stdout
                val output = stringIO.callAttr("getvalue").toString().trim()
                Log.d("MediaFetcherRepository", "Raw Python Output: $output")
                
                if (output.isEmpty()) {
                    throw Exception("No output received from Python script.")
                }
                
                val listType = object : TypeToken<List<MediaResult>>() {}.type
                val results: List<MediaResult> = gson.fromJson(output, listType)
                
                // If the script returned an error inside the JSON list, throw it
                val firstError = results.firstOrNull()?.error
                if (firstError != null) {
                    throw Exception(firstError)
                }
                
                results
            } finally {
                // Always restore original stdout
                sys.put("stdout", originalStdout)
            }
        } catch (e: Exception) {
            Log.e("MediaFetcherRepository", "Error executing mo3.py via Python", e)
            throw e
        }
    }
}

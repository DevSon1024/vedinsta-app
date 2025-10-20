// src/main/java/com/devson/vedinsta/VedInstaApplication.kt
package com.devson.vedinsta

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class VedInstaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start Python in the background when the app launches
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
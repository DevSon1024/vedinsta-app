package com.devson.vedinsta.notification

import android.content.Context
import androidx.startup.Initializer

class NotificationInitializer : Initializer<VedInstaNotificationManager> {
    override fun create(context: Context): VedInstaNotificationManager {
        return VedInstaNotificationManager.getInstance(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

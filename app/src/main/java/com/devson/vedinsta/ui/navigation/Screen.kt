package com.devson.vedinsta.ui.navigation

import com.devson.vedinsta.database.DownloadedPost

sealed class Screen {
    object Home : Screen()
    object DownloaderDetails : Screen()
    object History : Screen()
    object Favorites : Screen()
    object Sessions : Screen()
    object Settings : Screen()
    object Appearance : Screen()
    object AdvancedSettings : Screen()
    data class PostView(val post: DownloadedPost) : Screen()
    object About : Screen()
    object Notifications : Screen()
    object Login : Screen()
    object PrivacyPolicy : Screen()
    object WhatsAppSaver : Screen()
}

internal fun getScreenOrderValue(screen: Screen): Int {
    return when (screen) {
        is Screen.Home -> 0
        is Screen.DownloaderDetails -> 1
        is Screen.History -> 2
        is Screen.Favorites -> 3
        is Screen.Sessions -> 4
        is Screen.Settings -> 5
        is Screen.Appearance -> 6
        is Screen.AdvancedSettings -> 12
        is Screen.Notifications -> 7
        is Screen.About -> 8
        is Screen.Login -> 9
        is Screen.PostView -> 10
        is Screen.PrivacyPolicy -> 11
        is Screen.WhatsAppSaver -> 13
    }
}

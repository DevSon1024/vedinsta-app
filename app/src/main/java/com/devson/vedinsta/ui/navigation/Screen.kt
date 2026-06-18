package com.devson.vedinsta.ui.navigation

sealed class Screen(val route: String) {
    object MainPager : Screen("main_pager")
    object Home : Screen("home")
    object DownloaderDetails : Screen("downloader_details")
    object History : Screen("history")
    object Favorites : Screen("favorites")
    object Sessions : Screen("sessions")
    object Settings : Screen("settings")
    object Appearance : Screen("appearance")
    object AdvancedSettings : Screen("advanced_settings")
    object About : Screen("about")
    object Notifications : Screen("notifications")
    object Login : Screen("login")
    object PrivacyPolicy : Screen("privacy_policy")
    object WhatsAppSaver : Screen("whatsapp_saver")
    
    object PostView : Screen("post_view/{postId}") {
        fun createRoute(postId: String) = "post_view/$postId"
    }
    
    object WhatsAppStatusView : Screen("whatsapp_status_view/{initialIndex}") {
        fun createRoute(initialIndex: Int) = "whatsapp_status_view/$initialIndex"
    }
    
    object InstagramStoryView : Screen("instagram_story_view/{username}/{initialIndex}") {
        fun createRoute(username: String, initialIndex: Int) = "instagram_story_view/$username/$initialIndex"
    }
}

internal fun getScreenOrderValue(screen: Screen): Int {
    return when (screen) {
        is Screen.MainPager -> -1
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
        is Screen.WhatsAppStatusView -> 14
        is Screen.InstagramStoryView -> 16
    }
}

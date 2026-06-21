package com.devson.vedinsta.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class DownloadQuotaManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOWNLOADS = "download_timestamps"
        private const val KEY_OVERSHADOW_QUOTA = "overshadow_quota"
        private const val TAG = "DownloadQuotaManager"
        
        // Quota limits
        const val LIMIT_HOURLY = 40
        const val LIMIT_DAILY = 150
        const val LIMIT_WEEKLY = 500
    }

    sealed class QuotaStatus {
        object Allowed : QuotaStatus()
        data class Exceeded(val limitType: LimitType, val resetTimeMs: Long) : QuotaStatus()
    }

    enum class LimitType {
        HOURLY, DAILY, WEEKLY
    }

    var isOvershadowEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERSHADOW_QUOTA, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERSHADOW_QUOTA, value).apply()

    @Synchronized
    fun recordDownload() {
        val now = System.currentTimeMillis()
        val timestamps = getTimestamps().toMutableList()
        timestamps.add(now)
        saveTimestamps(timestamps)
        cleanOldTimestamps()
    }

    @Synchronized
    fun getTimestamps(): List<Long> {
        val raw = prefs.getString(KEY_DOWNLOADS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    @Synchronized
    private fun saveTimestamps(list: List<Long>) {
        val raw = list.joinToString(",")
        prefs.edit().putString(KEY_DOWNLOADS, raw).apply()
    }

    @Synchronized
    fun cleanOldTimestamps() {
        val cutoff = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L) // 14 days
        val timestamps = getTimestamps()
        val cleaned = timestamps.filter { it >= cutoff }
        if (cleaned.size != timestamps.size) {
            saveTimestamps(cleaned)
        }
    }

    private fun getHourlyCutoff(now: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getDailyCutoff(now: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getWeeklyCutoff(now: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val daysToSubtract = (dayOfWeek - java.util.Calendar.MONDAY + 7) % 7
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysToSubtract)
        return calendar.timeInMillis
    }

    fun checkQuota(): QuotaStatus {
        if (isOvershadowEnabled) {
            return QuotaStatus.Allowed
        }
        
        val now = System.currentTimeMillis()
        val timestamps = getTimestamps()
        
        val hourlyCutoff = getHourlyCutoff(now)
        val dailyCutoff = getDailyCutoff(now)
        val weeklyCutoff = getWeeklyCutoff(now)
        
        val hourlyCount = timestamps.count { it >= hourlyCutoff }
        val dailyCount = timestamps.count { it >= dailyCutoff }
        val weeklyCount = timestamps.count { it >= weeklyCutoff }
        
        if (hourlyCount >= LIMIT_HOURLY) {
            val resetTime = hourlyCutoff + (60 * 60 * 1000L)
            return QuotaStatus.Exceeded(LimitType.HOURLY, resetTime)
        }
        
        if (dailyCount >= LIMIT_DAILY) {
            val resetTime = dailyCutoff + (24 * 60 * 60 * 1000L)
            return QuotaStatus.Exceeded(LimitType.DAILY, resetTime)
        }
        
        if (weeklyCount >= LIMIT_WEEKLY) {
            val resetTime = weeklyCutoff + (7 * 24 * 60 * 60 * 1000L)
            return QuotaStatus.Exceeded(LimitType.WEEKLY, resetTime)
        }
        
        return QuotaStatus.Allowed
    }

    data class QuotaStats(
        val hourlyCount: Int,
        val hourlyLimit: Int,
        val hourlyResetMs: Long,
        val dailyCount: Int,
        val dailyLimit: Int,
        val dailyResetMs: Long,
        val weeklyCount: Int,
        val weeklyLimit: Int,
        val weeklyResetMs: Long
    )

    fun getQuotaStats(): QuotaStats {
        val now = System.currentTimeMillis()
        val timestamps = getTimestamps()
        
        val hourlyCutoff = getHourlyCutoff(now)
        val dailyCutoff = getDailyCutoff(now)
        val weeklyCutoff = getWeeklyCutoff(now)
        
        val hourlyCount = timestamps.count { it >= hourlyCutoff }
        val dailyCount = timestamps.count { it >= dailyCutoff }
        val weeklyCount = timestamps.count { it >= weeklyCutoff }
        
        val hourlyReset = hourlyCutoff + (60 * 60 * 1000L)
        val dailyReset = dailyCutoff + (24 * 60 * 60 * 1000L)
        val weeklyReset = weeklyCutoff + (7 * 24 * 60 * 60 * 1000L)
        
        return QuotaStats(
            hourlyCount = hourlyCount,
            hourlyLimit = LIMIT_HOURLY,
            hourlyResetMs = hourlyReset,
            dailyCount = dailyCount,
            dailyLimit = LIMIT_DAILY,
            dailyResetMs = dailyReset,
            weeklyCount = weeklyCount,
            weeklyLimit = LIMIT_WEEKLY,
            weeklyResetMs = weeklyReset
        )
    }
}

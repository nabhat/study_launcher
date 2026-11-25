package com.gatefocus.launcher.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoItem(
    val file: File,
    val title: String,
    val playlistName: String,
    val thumbnailFile: File?,
    var progressMs: Long = 0L,
    var durationMs: Long = 0L,
    var isCompleted: Boolean = false
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((progressMs * 100) / durationMs).toInt() else 0
}

data class PdfItem(
    val file: File,
    val title: String,
    val folderName: String = "Workbooks",
    var lastPage: Int = 0
)

class StudyProgressManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gate_study_progress", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun saveVideoProgress(filePath: String, currentPositionMs: Long, durationMs: Long) {
        val completed = durationMs > 0 && (currentPositionMs.toDouble() / durationMs) >= 0.88
        prefs.edit()
            .putLong("${filePath}_pos", currentPositionMs)
            .putLong("${filePath}_dur", durationMs)
            .putBoolean("${filePath}_done", completed)
            .apply()
    }

    fun getVideoProgress(filePath: String): Long {
        return prefs.getLong("${filePath}_pos", 0L)
    }

    fun getVideoDuration(filePath: String): Long {
        return prefs.getLong("${filePath}_dur", 0L)
    }

    fun isVideoCompleted(filePath: String): Boolean {
        return prefs.getBoolean("${filePath}_done", false)
    }

    fun setVideoCompleted(filePath: String, completed: Boolean) {
        val editor = prefs.edit().putBoolean("${filePath}_done", completed)
        if (!completed) {
            val dur = getVideoDuration(filePath)
            val pos = getVideoProgress(filePath)
            // Clear near-end timestamp so unmarking doesn't immediately re-trigger auto-complete
            if (dur > 0 && (pos.toDouble() / dur) >= 0.88) {
                editor.putLong("${filePath}_pos", 0L)
            }
        }
        editor.apply()
    }

    fun saveLastPlayedVideo(filePath: String) {
        prefs.edit().putString("last_played_video_path", filePath).apply()
    }

    fun getLastPlayedVideo(): String? {
        return prefs.getString("last_played_video_path", null)
    }

    fun savePreferredSpeedIndex(index: Int) {
        prefs.edit().putInt("preferred_speed_index", index).apply()
    }

    fun getPreferredSpeedIndex(): Int {
        return prefs.getInt("preferred_speed_index", 0)
    }

    fun setHideCompleted(hide: Boolean) {
        prefs.edit().putBoolean("hide_completed_videos", hide).apply()
    }

    fun getHideCompleted(): Boolean {
        return prefs.getBoolean("hide_completed_videos", false)
    }

    fun savePdfPage(filePath: String, pageIndex: Int) {
        prefs.edit().putInt("${filePath}_page", pageIndex).apply()
    }

    fun getPdfPage(filePath: String): Int {
        return prefs.getInt("${filePath}_page", 0)
    }

    // --- Study Stats & Streak Tracking ---

    private fun getTodayDateKey(): String = dateFormat.format(Date())

    private fun getYesterdayDateKey(): String =
        dateFormat.format(Date(System.currentTimeMillis() - 86400000L))

    fun addStudySeconds(seconds: Long) {
        if (seconds <= 0) return
        val today = getTodayDateKey()
        val current = prefs.getLong("study_seconds_$today", 0L)
        prefs.edit().putLong("study_seconds_$today", current + seconds).apply()
        updateStreak(today)
    }

    fun getTodayStudySeconds(): Long {
        val today = getTodayDateKey()
        return prefs.getLong("study_seconds_$today", 0L)
    }

    fun getTodayStudyFormatted(): String {
        val totalSecs = getTodayStudySeconds()
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else if (mins > 0) {
            "${mins}m"
        } else {
            "0m"
        }
    }

    fun getStudyStreak(): Int {
        val lastDate = prefs.getString("last_study_date", "") ?: ""
        val today = getTodayDateKey()
        val yesterday = getYesterdayDateKey()

        // If studied today or yesterday, streak is alive; otherwise reset to 0
        return if (lastDate == today || lastDate == yesterday) {
            prefs.getInt("study_streak_days", 1).coerceAtLeast(1)
        } else if (lastDate.isEmpty()) {
            0
        } else {
            0
        }
    }

    private fun updateStreak(todayKey: String) {
        val lastDate = prefs.getString("last_study_date", "") ?: ""
        if (lastDate != todayKey) {
            val yesterday = getYesterdayDateKey()
            val currentStreak = prefs.getInt("study_streak_days", 0)
            val newStreak = if (lastDate == yesterday) currentStreak + 1 else 1
            prefs.edit()
                .putString("last_study_date", todayKey)
                .putInt("study_streak_days", newStreak)
                .apply()
        }
    }
}

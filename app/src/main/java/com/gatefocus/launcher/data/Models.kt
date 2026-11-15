package com.gatefocus.launcher.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

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
    var lastPage: Int = 0
)

class StudyProgressManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gate_study_progress", Context.MODE_PRIVATE)

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

    fun savePdfPage(filePath: String, pageIndex: Int) {
        prefs.edit().putInt("${filePath}_page", pageIndex).apply()
    }

    fun getPdfPage(filePath: String): Int {
        return prefs.getInt("${filePath}_page", 0)
    }
}

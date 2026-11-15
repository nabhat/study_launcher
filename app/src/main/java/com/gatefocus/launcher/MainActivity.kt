package com.gatefocus.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gatefocus.launcher.data.PdfItem
import com.gatefocus.launcher.data.StudyProgressManager
import com.gatefocus.launcher.data.VideoItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var rvVideos: RecyclerView
    private lateinit var rvPdfs: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var btnKioskToggle: MaterialButton
    private lateinit var progressManager: StudyProgressManager

    private val videoList = mutableListOf<VideoItem>()
    private val pdfList = mutableListOf<PdfItem>()

    private var isKioskActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressManager = StudyProgressManager(this)

        rvVideos = findViewById(R.id.rvVideos)
        rvPdfs = findViewById(R.id.rvPdfs)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tabLayout = findViewById(R.id.tabLayout)
        btnKioskToggle = findViewById(R.id.btnKioskToggle)
        val btnRefresh = findViewById<ImageButton>(R.id.btnRefresh)

        rvVideos.layoutManager = LinearLayoutManager(this)
        rvPdfs.layoutManager = LinearLayoutManager(this)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        rvVideos.visibility = View.VISIBLE
                        rvPdfs.visibility = View.GONE
                        checkEmptyState(videoList.isEmpty())
                    }
                    1 -> {
                        rvVideos.visibility = View.GONE
                        rvPdfs.visibility = View.VISIBLE
                        checkEmptyState(pdfList.isEmpty())
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnRefresh.setOnClickListener {
            loadStudyFiles()
        }

        btnKioskToggle.setOnClickListener {
            handleKioskToggle()
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            loadStudyFiles()
        }
    }

    private fun handleKioskToggle() {
        if (!isKioskActive) {
            // Lock device
            if (!KioskManager.isDeviceAdmin(this)) {
                showDeviceAdminPrompt()
                return
            }
            KioskManager.startKioskMode(this)
            isKioskActive = true
            btnKioskToggle.text = "Locked 🔒"
            btnKioskToggle.setTextColor(getColor(R.color.red))
        } else {
            // Prompt for PIN to unlock
            val input = EditText(this).apply {
                hint = "Enter Master PIN"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Unlock Device")
                .setMessage("Enter your emergency PIN to temporarily unlock device:")
                .setView(input)
                .setPositiveButton("Unlock") { _, _ ->
                    val enteredPin = input.text.toString().trim()
                    if (enteredPin == KioskManager.getPin(this)) {
                        KioskManager.stopKioskMode(this)
                        isKioskActive = false
                        btnKioskToggle.text = "Lock Device"
                        btnKioskToggle.setTextColor(getColor(R.color.accent_green))
                    } else {
                        Toast.makeText(this, "Incorrect PIN. Device remains locked.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showDeviceAdminPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Activate Device Admin")
            .setMessage("To prevent uninstallation and enforce strict study hours without bypasses, activate Device Admin for GateFocus.")
            .setPositiveButton("Activate") { _, _ ->
                KioskManager.requestDeviceAdmin(this)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun checkEmptyState(isEmpty: Boolean) {
        tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun loadStudyFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val videos = mutableListOf<VideoItem>()
            val pdfs = mutableListOf<PdfItem>()

            val searchDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "GATE"),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                getExternalFilesDir(null)
            ).filterNotNull().filter { it.exists() }.distinctBy { it.absolutePath }

            for (dir in searchDirs) {
                scanDirectory(dir, videos, pdfs)
            }

            withContext(Dispatchers.Main) {
                videoList.clear()
                videoList.addAll(videos)
                rvVideos.adapter = VideoAdapter(videoList) { selectedVideo ->
                    VideoPlayerActivity.start(this@MainActivity, selectedVideo.file.absolutePath, selectedVideo.title)
                }

                pdfList.clear()
                pdfList.addAll(pdfs)
                rvPdfs.adapter = PdfAdapter(pdfList) { selectedPdf ->
                    PdfViewerActivity.start(this@MainActivity, selectedPdf.file.absolutePath, selectedPdf.title)
                }

                if (tabLayout.selectedTabPosition == 0) {
                    checkEmptyState(videoList.isEmpty())
                } else {
                    checkEmptyState(pdfList.isEmpty())
                }
            }
        }
    }

    private fun scanDirectory(dir: File, videos: MutableList<VideoItem>, pdfs: MutableList<PdfItem>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, videos, pdfs)
            } else {
                val ext = file.extension.lowercase()
                if (ext in listOf("mp4", "mkv", "webm")) {
                    val baseName = file.nameWithoutExtension
                    val thumbJpg = File(file.parentFile, "$baseName.jpg")
                    val thumbWebp = File(file.parentFile, "$baseName.webp")
                    val thumbFile = when {
                        thumbJpg.exists() -> thumbJpg
                        thumbWebp.exists() -> thumbWebp
                        else -> null
                    }

                    val playlistName = file.parentFile?.name ?: "Lectures"
                    val item = VideoItem(
                        file = file,
                        title = file.nameWithoutExtension,
                        playlistName = playlistName,
                        thumbnailFile = thumbFile,
                        progressMs = progressManager.getVideoProgress(file.absolutePath),
                        durationMs = progressManager.getVideoDuration(file.absolutePath),
                        isCompleted = progressManager.isVideoCompleted(file.absolutePath)
                    )
                    videos.add(item)
                } else if (ext == "pdf") {
                    val item = PdfItem(
                        file = file,
                        title = file.nameWithoutExtension,
                        lastPage = progressManager.getPdfPage(file.absolutePath)
                    )
                    pdfs.add(item)
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        if (!hasStoragePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    101
                )
            }
        } else {
            loadStudyFiles()
            checkAccessibilityPermission()
        }
    }

    private fun checkAccessibilityPermission() {
        if (!FocusGuardianService.isAccessibilityServiceEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("2nd Line of Defence: App Blocker")
                .setMessage("To automatically block and close any distracting apps (File Manager, Chrome, novels, etc.) if opened via notifications or external links, enable GateFocus in Accessibility Settings.")
                .setPositiveButton("Enable Blocker") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }
}

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
    private lateinit var btnAllowedApps: MaterialButton
    private lateinit var btnChangePin: MaterialButton
    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvAdminBadge: TextView
    private lateinit var progressManager: StudyProgressManager

    private val videoList = mutableListOf<VideoItem>()
    private val pdfList = mutableListOf<PdfItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressManager = StudyProgressManager(this)

        rvVideos = findViewById(R.id.rvVideos)
        rvPdfs = findViewById(R.id.rvPdfs)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tabLayout = findViewById(R.id.tabLayout)
        btnKioskToggle = findViewById(R.id.btnKioskToggle)
        btnAllowedApps = findViewById(R.id.btnAllowedApps)
        btnChangePin = findViewById(R.id.btnChangePin)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvAdminBadge = findViewById(R.id.tvAdminBadge)
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

        btnAllowedApps.setOnClickListener {
            showAllowedAppsDialog()
        }

        btnChangePin.setOnClickListener {
            showChangePinDialog()
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateProtectionCardStatus()
        if (hasStoragePermission()) {
            loadStudyFiles()
        }
    }

    private fun updateProtectionCardStatus() {
        val isAdmin = KioskManager.isDeviceAdmin(this)
        val isLockActive = KioskManager.isFocusLockActive(this)

        if (isAdmin && isLockActive) {
            tvProtectionStatus.text = "🛡️ Tamper Protection: Active"
            tvProtectionStatus.setTextColor(getColor(R.color.accent_green))
            tvAdminBadge.text = if (KioskManager.isDeviceOwner(this)) "DEVICE OWNER" else "ADMIN LOCKED"
            tvAdminBadge.setTextColor(getColor(R.color.accent_green))
            btnKioskToggle.text = "Locked 🔒"
            btnKioskToggle.setTextColor(getColor(R.color.red))
        } else if (!isAdmin) {
            tvProtectionStatus.text = "⚠️ Device Admin: Not Activated"
            tvProtectionStatus.setTextColor(getColor(R.color.red))
            tvAdminBadge.text = "UNPROTECTED"
            tvAdminBadge.setTextColor(getColor(R.color.red))
            btnKioskToggle.text = "Activate Admin"
            btnKioskToggle.setTextColor(getColor(R.color.accent_green))
        } else {
            tvProtectionStatus.text = "🛡️ Focus Mode: Standby"
            tvProtectionStatus.setTextColor(getColor(R.color.accent))
            tvAdminBadge.text = "ADMIN READY"
            tvAdminBadge.setTextColor(getColor(R.color.accent))
            btnKioskToggle.text = "Lock Device"
            btnKioskToggle.setTextColor(getColor(R.color.accent_green))
        }
    }

    private fun handleKioskToggle() {
        if (!KioskManager.isDeviceAdmin(this)) {
            showDeviceAdminPrompt()
            return
        }

        val isLockActive = KioskManager.isFocusLockActive(this)
        if (!isLockActive) {
            KioskManager.startKioskMode(this)
            updateProtectionCardStatus()
        } else {
            val input = EditText(this).apply {
                hint = "Enter Master PIN"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Unlock Device")
                .setMessage("Enter Master PIN to temporarily deactivate Focus Lock:")
                .setView(input)
                .setPositiveButton("Unlock") { _, _ ->
                    val enteredPin = input.text.toString().trim()
                    if (enteredPin == KioskManager.getPin(this)) {
                        KioskManager.stopKioskMode(this)
                        updateProtectionCardStatus()
                    } else {
                        Toast.makeText(this, "Incorrect PIN. Focus Lock remains active.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showAllowedAppsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val installed = pm.queryIntentActivities(intent, 0)
            val currentAllowed = KioskManager.getAllowedApps(this@MainActivity)

            val appEntries = installed
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    val pkg = it.activityInfo.packageName
                    val label = it.loadLabel(pm).toString()
                    Triple(pkg, label, currentAllowed.contains(pkg))
                }
                .sortedBy { it.second.lowercase() }

            val labels = appEntries.map { it.second }.toTypedArray()
            val checked = appEntries.map { it.third }.toBooleanArray()

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Allowed Study Apps (Whitelist)")
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        val newAllowed = mutableSetOf<String>()
                        for (i in appEntries.indices) {
                            if (checked[i]) {
                                newAllowed.add(appEntries[i].first)
                            }
                        }
                        KioskManager.setAllowedApps(this@MainActivity, newAllowed)
                        Toast.makeText(this@MainActivity, "Whitelist updated", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showChangePinDialog() {
        val currentPin = KioskManager.getPin(this)
        val input = EditText(this).apply {
            hint = "New 4-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Change Master PIN")
            .setMessage("Set a new PIN to protect settings and unlocks (Current PIN: $currentPin):")
            .setView(input)
            .setPositiveButton("Save PIN") { _, _ ->
                val newPin = input.text.toString().trim()
                if (newPin.length >= 4) {
                    KioskManager.setPin(this, newPin)
                    Toast.makeText(this, "Master PIN updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

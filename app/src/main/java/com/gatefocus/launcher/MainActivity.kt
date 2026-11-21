package com.gatefocus.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var btnKioskToggle: MaterialButton
    private lateinit var btnAllowedApps: MaterialButton
    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvAdminBadge: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerSubtext: TextView
    private lateinit var scrollDurationPresets: HorizontalScrollView
    private lateinit var progressManager: StudyProgressManager

    private var selectedDurationMinutes = 45
    private var sessionTimer: CountDownTimer? = null
    private val durationButtons = mutableListOf<MaterialButton>()

    private val videoList = mutableListOf<VideoItem>()
    private val pdfList = mutableListOf<PdfItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressManager = StudyProgressManager(this)
        selectedDurationMinutes = KioskManager.getSelectedDuration(this)

        rvVideos = findViewById(R.id.rvVideos)
        rvPdfs = findViewById(R.id.rvPdfs)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        tabLayout = findViewById(R.id.tabLayout)
        btnKioskToggle = findViewById(R.id.btnKioskToggle)
        btnAllowedApps = findViewById(R.id.btnAllowedApps)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvAdminBadge = findViewById(R.id.tvAdminBadge)
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay)
        tvTimerSubtext = findViewById(R.id.tvTimerSubtext)
        scrollDurationPresets = findViewById(R.id.scrollDurationPresets)
        val btnRefresh = findViewById<MaterialButton>(R.id.btnRefresh)

        setupDurationPresets()

        rvVideos.layoutManager = LinearLayoutManager(this)
        rvPdfs.layoutManager = LinearLayoutManager(this)

        if (tabLayout.tabCount == 0) {
            tabLayout.addTab(tabLayout.newTab().setText("📹 Video Lectures"))
            tabLayout.addTab(tabLayout.newTab().setText("📄 PDF Workbooks"))
        }

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
            if (KioskManager.isFocusLockActive(this)) {
                Toast.makeText(this, "Allowed apps list is locked while Focus Session is active.", Toast.LENGTH_SHORT).show()
            } else {
                showAllowedAppsDialog()
            }
        }

        checkAndRequestPermissions()

        if (intent?.getBooleanExtra("clear_owner", false) == true) {
            KioskManager.removeAllRestrictionsAndDeviceOwner(this)
            Toast.makeText(this, "Device Owner & restrictions removed.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("clear_owner", false)) {
            KioskManager.removeAllRestrictionsAndDeviceOwner(this)
            Toast.makeText(this, "Device Owner & restrictions removed.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateProtectionCardStatus()
        if (hasStoragePermission()) {
            loadStudyFiles()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionTimer?.cancel()
    }

    private fun setupDurationPresets() {
        val pairs = listOf(
            Pair(R.id.btnDuration15, 15),
            Pair(R.id.btnDuration25, 25),
            Pair(R.id.btnDuration45, 45),
            Pair(R.id.btnDuration60, 60),
            Pair(R.id.btnDuration90, 90),
            Pair(R.id.btnDuration120, 120)
        )

        for ((id, duration) in pairs) {
            val btn = findViewById<MaterialButton>(id)
            durationButtons.add(btn)
            btn.setOnClickListener {
                if (!KioskManager.isFocusLockActive(this)) {
                    selectedDurationMinutes = duration
                    KioskManager.setSelectedDuration(this, duration)
                    updateDurationButtonsSelection()
                    updateProtectionCardStatus()
                }
            }
        }
        updateDurationButtonsSelection()
    }

    private fun updateDurationButtonsSelection() {
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)

        val durations = listOf(15, 25, 45, 60, 90, 120)
        for (i in durationButtons.indices) {
            val btn = durationButtons[i]
            val duration = durations[i]
            if (duration == selectedDurationMinutes) {
                btn.setTextColor(accentColor)
                btn.strokeColor = ContextCompat.getColorStateList(this, R.color.accent)
            } else {
                btn.setTextColor(textPrimaryColor)
                btn.strokeColor = ContextCompat.getColorStateList(this, R.color.card_border)
            }
        }
    }

    private fun updateProtectionCardStatus() {
        val isAdmin = KioskManager.isDeviceAdmin(this)
        val isAccessibility = FocusGuardianService.isAccessibilityServiceEnabled(this)
        val isLockActive = KioskManager.isFocusLockActive(this)

        if (isAdmin && isAccessibility && isLockActive) {
            tvProtectionStatus.text = "🛡️ Focus Shield: Active"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvAdminBadge.text = if (KioskManager.isDeviceOwner(this)) "DEVICE OWNER" else "LOCKED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            scrollDurationPresets.visibility = View.GONE
            tvTimerSubtext.text = "Focus session active. Settings and distractions are strictly blocked."
            btnKioskToggle.isEnabled = false
            btnKioskToggle.alpha = 0.5f
            btnKioskToggle.text = "Focus Locked 🔒"
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeColor = ContextCompat.getColorStateList(this, R.color.accent_green)

            btnAllowedApps.isEnabled = false
            btnAllowedApps.alpha = 0.4f
            btnAllowedApps.text = "Apps Locked 🔒"

            startLiveSessionTimer()
        } else if (!isAdmin) {
            sessionTimer?.cancel()
            tvProtectionStatus.text = "⚠️ Device Admin: Not Activated"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
            tvAdminBadge.text = "UNPROTECTED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.red))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "⏱️ Focus Duration: $selectedDurationMinutes min"
            tvTimerSubtext.text = "Activate Device Admin to enable tamper protection"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Activate Admin"
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeColor = ContextCompat.getColorStateList(this, R.color.accent_green)

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Allowed Apps ⚙"
        } else if (!isAccessibility) {
            sessionTimer?.cancel()
            tvProtectionStatus.text = "⚠️ App Blocker: Accessibility Required"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            tvAdminBadge.text = "SETUP REQUIRED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "⏱️ Focus Duration: $selectedDurationMinutes min"
            tvTimerSubtext.text = "Enable GateFocus in Accessibility Settings to block Settings & apps"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Enable App Blocker"
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeColor = ContextCompat.getColorStateList(this, R.color.accent_green)

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Allowed Apps ⚙"
        } else if (!isDefaultLauncher()) {
            sessionTimer?.cancel()
            tvProtectionStatus.text = "⚠️ Home Launcher: Not Set"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            tvAdminBadge.text = "SET AS HOME"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "⏱️ Focus Duration: $selectedDurationMinutes min"
            tvTimerSubtext.text = "Set GateFocus as default Home launcher to prevent navigation glitching"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Set Default Launcher 🏠"
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeColor = ContextCompat.getColorStateList(this, R.color.accent_green)

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Allowed Apps ⚙"
        } else {
            sessionTimer?.cancel()
            tvProtectionStatus.text = "🛡️ Focus Shield: Standby"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            tvAdminBadge.text = "READY"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "⏱️ Focus Duration: $selectedDurationMinutes min"
            tvTimerSubtext.text = "Select study duration and lock distractions"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Start Focus (${selectedDurationMinutes}m) 🔒"
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeColor = ContextCompat.getColorStateList(this, R.color.accent_green)

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Allowed Apps ⚙"
        }
    }

    private fun startLiveSessionTimer() {
        sessionTimer?.cancel()
        val remainingMillis = KioskManager.getRemainingTimeMillis(this)
        if (remainingMillis <= 0L) {
            updateProtectionCardStatus()
            return
        }

        sessionTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSecs = millisUntilFinished / 1000
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                val formatted = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format("%02d:%02d", mins, secs)
                }
                tvTimerDisplay.text = "⏳ $formatted remaining"
            }

            override fun onFinish() {
                KioskManager.stopKioskMode(this@MainActivity)
                updateProtectionCardStatus()
                Toast.makeText(this@MainActivity, "🎉 Focus session completed! Great study session.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun handleKioskToggle() {
        if (!KioskManager.isDeviceAdmin(this)) {
            showDeviceAdminPrompt()
            return
        }
        if (!FocusGuardianService.isAccessibilityServiceEnabled(this)) {
            showAccessibilityPrompt()
            return
        }
        if (!isDefaultLauncher()) {
            showSetDefaultLauncherPrompt()
            return
        }

        val isLockActive = KioskManager.isFocusLockActive(this)
        if (!isLockActive) {
            KioskManager.startFocusSession(this, selectedDurationMinutes)
            updateProtectionCardStatus()
        } else {
            Toast.makeText(this, "Focus session is active until the timer finishes.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        } catch (e: Exception) {
            false
        }
    }

    private fun promptSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    startActivity(intent)
                    return
                }
            }
        }
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    private fun showSetDefaultLauncherPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Set as Default Launcher")
            .setMessage("To ensure distraction blocking runs without glitching and pressing Home stays inside your study hub, set GateFocus as your default Home launcher.")
            .setPositiveButton("Set as Default") { _, _ ->
                promptSetDefaultLauncher()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                KioskManager.startFocusSession(this, selectedDurationMinutes)
                updateProtectionCardStatus()
            }
            .show()
    }

    private fun showAccessibilityPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("GateFocus needs Accessibility Service enabled to block Settings and distracting apps during your focus session. Please enable GateFocus in Accessibility Settings.")
            .setPositiveButton("Enable Now") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAllowedAppsDialog() {
        if (KioskManager.isFocusLockActive(this)) {
            Toast.makeText(this, "Allowed apps cannot be modified while Focus Session is active.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (KioskManager.isFocusLockActive(this@MainActivity)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Allowed apps cannot be modified while Focus Session is active.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val installed = pm.queryIntentActivities(intent, 0)
            val currentAllowed = KioskManager.getAllowedApps(this@MainActivity)

            val appEntries = installed
                .filter {
                    val pkg = it.activityInfo.packageName
                    pkg != packageName && !FocusGuardianService.isSettingsPackage(pkg)
                }
                .map {
                    val pkg = it.activityInfo.packageName
                    val label = it.loadLabel(pm).toString()
                    Triple(pkg, label, currentAllowed.contains(pkg))
                }
                .sortedBy { it.second.lowercase() }

            val labels: Array<CharSequence> = Array(appEntries.size) { i -> appEntries[i].second }
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
                        val saved = KioskManager.setAllowedApps(this@MainActivity, newAllowed)
                        if (saved) {
                            Toast.makeText(this@MainActivity, "Whitelist updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Cannot modify whitelist during active focus session.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
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
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            tvEmptyMessage.text = if (tabLayout.selectedTabPosition == 0) {
                "No video lectures found in /GATE/"
            } else {
                "No PDF workbooks found in /GATE/"
            }
        }
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

            // Natural alphanumeric sort (e.g. 01, 02, 10...)
            videos.sortWith { v1, v2 ->
                val folderCmp = NaturalOrderComparator.compare(v1.playlistName, v2.playlistName)
                if (folderCmp != 0) folderCmp
                else NaturalOrderComparator.compare(v1.file.name, v2.file.name)
            }

            pdfs.sortWith { p1, p2 ->
                NaturalOrderComparator.compare(p1.file.name, p2.file.name)
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

object NaturalOrderComparator : Comparator<String> {
    private val SPLIT_REGEX = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val parts1 = s1.split(SPLIT_REGEX)
        val parts2 = s2.split(SPLIT_REGEX)
        val minLen = minOf(parts1.size, parts2.size)

        for (i in 0 until minLen) {
            val p1 = parts1[i]
            val p2 = parts2[i]
            val num1 = p1.toLongOrNull()
            val num2 = p2.toLongOrNull()

            val diff = if (num1 != null && num2 != null) {
                num1.compareTo(num2)
            } else {
                p1.compareTo(p2, ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return parts1.size.compareTo(parts2.size)
    }
}

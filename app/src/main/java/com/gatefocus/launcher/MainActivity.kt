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
import android.text.Editable
import android.text.TextWatcher
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gatefocus.launcher.data.PdfItem
import com.gatefocus.launcher.data.StudyProgressManager
import com.gatefocus.launcher.data.TitleFormatter
import com.gatefocus.launcher.data.VideoItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private lateinit var ivShieldIcon: ImageView
    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvAdminBadge: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerSubtext: TextView
    private lateinit var scrollDurationPresets: HorizontalScrollView
    private lateinit var progressManager: StudyProgressManager

    // Launcher Switching Controls
    private lateinit var btnSwitchHome: MaterialButton
    private lateinit var btnHomeSettings: MaterialButton
    private lateinit var layoutSessionComplete: MaterialCardView
    private lateinit var btnCompleteSwitchHome: MaterialButton
    private lateinit var btnCompleteDismiss: MaterialButton

    // Study Stats & Dashboard
    private var cardStudyStats: View? = null
    private var rootContainer: ViewGroup? = null
    private var layoutFocusHeader: View? = null
    private var layoutFocusDetails: View? = null
    private var ivExpandFocus: ImageView? = null
    private var isFocusPanelExpanded = false
    private lateinit var tvStatsTimeToday: TextView
    private lateinit var tvStatsCompleted: TextView
    private lateinit var tvStatsStreak: TextView

    // Subject Organization & Search
    private lateinit var etSearch: EditText
    private lateinit var chipGroupSubjects: ChipGroup
    private lateinit var scrollSubjects: HorizontalScrollView
    private lateinit var chipHideDone: Chip
    private lateinit var cardUpNext: MaterialCardView
    private lateinit var tvUpNextLabel: TextView
    private lateinit var tvUpNextTitle: TextView
    private lateinit var btnUpNextPlay: MaterialButton

    private var selectedDurationMinutes = 45
    private var sessionTimer: CountDownTimer? = null
    private val durationButtons = mutableListOf<MaterialButton>()

    // Data lists
    private val allVideos = mutableListOf<VideoItem>()
    private val allPdfs = mutableListOf<PdfItem>()
    private val videoList = mutableListOf<VideoItem>()
    private val pdfList = mutableListOf<PdfItem>()

    private var selectedSubject: String = "All"
    private var searchQuery: String = ""
    private var hideCompleted: Boolean = false
    private var upNextVideo: VideoItem? = null

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateProtectionCardStatus()
    }

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
        ivShieldIcon = findViewById(R.id.ivShieldIcon)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvAdminBadge = findViewById(R.id.tvAdminBadge)
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay)
        tvTimerSubtext = findViewById(R.id.tvTimerSubtext)
        scrollDurationPresets = findViewById(R.id.scrollDurationPresets)
        val btnRefresh = findViewById<MaterialButton>(R.id.btnRefresh)

        // Launcher switching views
        btnSwitchHome = findViewById(R.id.btnSwitchHome)
        btnHomeSettings = findViewById(R.id.btnHomeSettings)
        layoutSessionComplete = findViewById(R.id.layoutSessionComplete)
        btnCompleteSwitchHome = findViewById(R.id.btnCompleteSwitchHome)
        btnCompleteDismiss = findViewById(R.id.btnCompleteDismiss)

        // Stats dashboard views
        rootContainer = findViewById(R.id.rootContainer)
        cardStudyStats = findViewById(R.id.cardStudyStats)
        layoutFocusHeader = findViewById(R.id.layoutFocusHeader)
        layoutFocusDetails = findViewById(R.id.layoutFocusDetails)
        ivExpandFocus = findViewById(R.id.ivExpandFocus)
        tvStatsTimeToday = findViewById(R.id.tvStatsTimeToday)
        tvStatsCompleted = findViewById(R.id.tvStatsCompleted)
        tvStatsStreak = findViewById(R.id.tvStatsStreak)

        layoutFocusHeader?.setOnClickListener {
            isFocusPanelExpanded = !isFocusPanelExpanded
            applyFocusDrawerState(animate = true)
        }

        // Subject & search views
        etSearch = findViewById(R.id.etSearch)
        chipGroupSubjects = findViewById(R.id.chipGroupSubjects)
        scrollSubjects = findViewById(R.id.scrollSubjects)
        chipHideDone = findViewById(R.id.chipHideDone)
        cardUpNext = findViewById(R.id.cardUpNext)
        tvUpNextLabel = findViewById(R.id.tvUpNextLabel)
        tvUpNextTitle = findViewById(R.id.tvUpNextTitle)
        btnUpNextPlay = findViewById(R.id.btnUpNextPlay)

        hideCompleted = progressManager.getHideCompleted()
        chipHideDone.isChecked = hideCompleted
        chipHideDone.setOnCheckedChangeListener { _, isChecked ->
            hideCompleted = isChecked
            progressManager.setHideCompleted(isChecked)
            applyFilter()
        }

        val launchUpNext = {
            upNextVideo?.let { openVideoLecture(it) }
        }
        cardUpNext.setOnClickListener { launchUpNext() }
        btnUpNextPlay.setOnClickListener { launchUpNext() }

        setupDurationPresets()

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rvVideos.layoutManager = if (isLandscape) GridLayoutManager(this, 2) else LinearLayoutManager(this)
        rvPdfs.layoutManager = if (isLandscape) GridLayoutManager(this, 2) else LinearLayoutManager(this)

        rvVideos.adapter = VideoAdapter(
            videos = videoList,
            onVideoClick = { selectedVideo -> openVideoLecture(selectedVideo) },
            onVideoLongClick = { selectedVideo -> showVideoCompletionDialog(selectedVideo) }
        )
        rvPdfs.adapter = PdfAdapter(pdfList) { selectedPdf ->
            PdfViewerActivity.start(this, selectedPdf.file.absolutePath, selectedPdf.title)
        }

        if (tabLayout.tabCount == 0) {
            tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_video).setText("Video Lectures"))
            tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_pdf).setText("PDF Workbooks"))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        rvVideos.visibility = View.VISIBLE
                        rvPdfs.visibility = View.GONE
                        updateUpNextVisibility()
                        checkEmptyState(videoList.isEmpty())
                    }
                    1 -> {
                        rvVideos.visibility = View.GONE
                        rvPdfs.visibility = View.VISIBLE
                        cardUpNext.visibility = View.GONE
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

        // Open built-in Apps Drawer directly inside GateFocus
        btnSwitchHome.setOnClickListener {
            AppsDrawerActivity.start(this)
        }

        btnHomeSettings.setOnClickListener {
            if (KioskManager.isFocusLockActive(this)) {
                Toast.makeText(this, "Home settings are locked while Focus Session is active.", Toast.LENGTH_SHORT).show()
            } else {
                showLauncherSelectionDialog()
            }
        }

        val btnMainBluetooth = findViewById<MaterialButton>(R.id.btnMainBluetooth)
        btnMainBluetooth.setOnClickListener {
            BluetoothDialogHelper.show(this)
        }

        btnCompleteSwitchHome.setOnClickListener {
            layoutSessionComplete.visibility = View.GONE
            AppsDrawerActivity.start(this)
        }

        btnCompleteDismiss.setOnClickListener {
            layoutSessionComplete.visibility = View.GONE
        }

        // Real-Time Search Watcher
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
        if (KioskManager.isDeviceOwner(this)) {
            KioskManager.applyDeviceOwnerPolicies(this)
        }
        updateProtectionCardStatus()
        updateStudyStats()
        if (hasStoragePermission()) {
            loadStudyFiles()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionTimer?.cancel()
    }

    private lateinit var btnDurationCustom: MaterialButton

    private fun formatDurationLabel(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    private fun setupDurationPresets() {
        val pairs = listOf(
            Pair(R.id.btnDuration15, 15),
            Pair(R.id.btnDuration25, 25),
            Pair(R.id.btnDuration45, 45),
            Pair(R.id.btnDuration60, 60),
            Pair(R.id.btnDuration90, 90),
            Pair(R.id.btnDuration120, 120),
            Pair(R.id.btnDuration180, 180),
            Pair(R.id.btnDuration240, 240),
            Pair(R.id.btnDuration360, 360),
            Pair(R.id.btnDuration480, 480)
        )

        durationButtons.clear()
        var matchedBtn: MaterialButton? = null
        for ((btnId, minutes) in pairs) {
            val btn = findViewById<MaterialButton>(btnId)
            durationButtons.add(btn)
            if (minutes == selectedDurationMinutes) {
                matchedBtn = btn
            }
            btn.setOnClickListener {
                selectedDurationMinutes = minutes
                KioskManager.setSelectedDuration(this, minutes)
                btnDurationCustom.text = "Custom"
                highlightSelectedPreset(btn)
                updateProtectionCardStatus()
            }
        }

        btnDurationCustom = findViewById(R.id.btnDurationCustom)
        durationButtons.add(btnDurationCustom)
        btnDurationCustom.setOnClickListener {
            showCustomDurationDialog()
        }

        if (matchedBtn != null) {
            btnDurationCustom.text = "Custom"
            highlightSelectedPreset(matchedBtn)
        } else {
            btnDurationCustom.text = "Custom (${formatDurationLabel(selectedDurationMinutes)})"
            highlightSelectedPreset(btnDurationCustom)
        }
    }

    private fun showCustomDurationDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 8)
        }
        val etHours = EditText(this).apply {
            hint = "Hours"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((selectedDurationMinutes / 60).toString())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvHrs = TextView(this).apply {
            text = "h  "
            textSize = 15f
            setPadding(8, 0, 16, 0)
        }
        val etMinutes = EditText(this).apply {
            hint = "Min"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((selectedDurationMinutes % 60).toString())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvMins = TextView(this).apply {
            text = "m"
            textSize = 15f
            setPadding(8, 0, 0, 0)
        }
        container.addView(etHours)
        container.addView(tvHrs)
        container.addView(etMinutes)
        container.addView(tvMins)

        AlertDialog.Builder(this)
            .setTitle("Custom Focus Duration")
            .setMessage("Enter how long to lock distractions (up to 24h):")
            .setView(container)
            .setPositiveButton("Set") { _, _ ->
                val hrs = etHours.text.toString().toIntOrNull() ?: 0
                val mins = etMinutes.text.toString().toIntOrNull() ?: 0
                val total = (hrs * 60 + mins).coerceIn(5, 1440)
                selectedDurationMinutes = total
                KioskManager.setSelectedDuration(this, total)
                btnDurationCustom.text = "Custom (${formatDurationLabel(total)})"
                highlightSelectedPreset(btnDurationCustom)
                updateProtectionCardStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyFocusDrawerState(animate: Boolean) {
        val details = layoutFocusDetails ?: return
        if (animate) {
            rootContainer?.let { TransitionManager.beginDelayedTransition(it) }
        }
        details.visibility = if (isFocusPanelExpanded) View.VISIBLE else View.GONE
        ivExpandFocus?.animate()?.rotation(if (isFocusPanelExpanded) 180f else 0f)?.setDuration(180)?.start()
    }

    private fun highlightSelectedPreset(selectedBtn: MaterialButton) {
        val inactiveColor = ContextCompat.getColor(this, R.color.primary_dark)
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val textSecondaryColor = ContextCompat.getColor(this, R.color.text_secondary)

        for (btn in durationButtons) {
            btn.strokeWidth = 0
            if (btn.id == selectedBtn.id) {
                btn.setBackgroundColor(accentColor)
                btn.setTextColor(inactiveColor)
            } else {
                btn.setBackgroundColor(inactiveColor)
                btn.setTextColor(textSecondaryColor)
            }
        }
    }

    private fun updateProtectionCardStatus() {
        val isAdmin = KioskManager.isDeviceAdmin(this)
        val isAccessibility = FocusGuardianService.isAccessibilityServiceEnabled(this)
        val isLockActive = KioskManager.isFocusLockActive(this)

        if (isAdmin && isAccessibility && isLockActive) {
            ivShieldIcon.setImageResource(R.drawable.ic_shield)
            ivShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_green)
            tvProtectionStatus.text = "Focus Shield: Active"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvAdminBadge.text = if (KioskManager.isDeviceOwner(this)) "DEVICE OWNER" else "LOCKED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            scrollDurationPresets.visibility = View.GONE
            tvTimerSubtext.text = "Focus session active. Distractions & launcher switching are locked."
            btnKioskToggle.isEnabled = false
            btnKioskToggle.alpha = 0.7f
            btnKioskToggle.text = "Locked"
            btnKioskToggle.setIconResource(R.drawable.ic_lock)
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeWidth = 0

            btnAllowedApps.isEnabled = false
            btnAllowedApps.alpha = 0.4f
            btnAllowedApps.text = "Locked"
            btnAllowedApps.setIconResource(R.drawable.ic_lock)

            // Allow opening built-in Apps Drawer even during Focus (shows only allowed study apps)
            btnSwitchHome.isEnabled = true
            btnSwitchHome.alpha = 1.0f
            btnHomeSettings.isEnabled = false
            btnHomeSettings.alpha = 0.4f

            applyFocusDrawerState(animate = false)
            startLiveSessionTimer()
        } else if (!isAdmin) {
            sessionTimer?.cancel()
            ivShieldIcon.setImageResource(R.drawable.ic_shield_alert)
            ivShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.red)
            tvProtectionStatus.text = "Device Admin: Not Activated"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
            tvAdminBadge.text = "UNPROTECTED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.red))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "Focus: ${formatDurationLabel(selectedDurationMinutes)}"
            tvTimerSubtext.text = "Activate Device Admin to enable tamper protection"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Activate"
            btnKioskToggle.setIconResource(R.drawable.ic_lock)
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeWidth = 0

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Apps"
            btnAllowedApps.setIconResource(R.drawable.ic_apps)

            btnSwitchHome.isEnabled = true
            btnSwitchHome.alpha = 1.0f
            btnHomeSettings.isEnabled = true
            btnHomeSettings.alpha = 1.0f
            isFocusPanelExpanded = true
            applyFocusDrawerState(animate = false)
        } else if (!isAccessibility) {
            sessionTimer?.cancel()
            ivShieldIcon.setImageResource(R.drawable.ic_shield_alert)
            ivShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_yellow)
            tvProtectionStatus.text = "App Blocker: Accessibility Required"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            tvAdminBadge.text = "SETUP REQUIRED"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "Focus: ${formatDurationLabel(selectedDurationMinutes)}"
            tvTimerSubtext.text = "Enable GateFocus in Accessibility Settings to block Settings & apps"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Enable"
            btnKioskToggle.setIconResource(R.drawable.ic_lock)
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeWidth = 0

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Apps"
            btnAllowedApps.setIconResource(R.drawable.ic_apps)

            btnSwitchHome.isEnabled = true
            btnSwitchHome.alpha = 1.0f
            btnHomeSettings.isEnabled = true
            btnHomeSettings.alpha = 1.0f
            isFocusPanelExpanded = true
            applyFocusDrawerState(animate = false)
        } else if (!isDefaultLauncher()) {
            sessionTimer?.cancel()
            ivShieldIcon.setImageResource(R.drawable.ic_shield_alert)
            ivShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_yellow)
            tvProtectionStatus.text = "Home Launcher: Not Set"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            tvAdminBadge.text = "SET HOME"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "Focus: ${formatDurationLabel(selectedDurationMinutes)}"
            tvTimerSubtext.text = "Set GateFocus as default Home launcher to prevent navigation glitching"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Set Home"
            btnKioskToggle.setIconResource(R.drawable.ic_home)
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeWidth = 0

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Apps"
            btnAllowedApps.setIconResource(R.drawable.ic_apps)

            btnSwitchHome.isEnabled = true
            btnSwitchHome.alpha = 1.0f
            btnHomeSettings.isEnabled = true
            btnHomeSettings.alpha = 1.0f
            isFocusPanelExpanded = true
            applyFocusDrawerState(animate = false)
        } else {
            sessionTimer?.cancel()
            ivShieldIcon.setImageResource(R.drawable.ic_shield)
            ivShieldIcon.imageTintList = ContextCompat.getColorStateList(this, if (KioskManager.isDeviceOwner(this)) R.color.accent_green else R.color.accent)
            tvProtectionStatus.text = if (KioskManager.isDeviceOwner(this)) "Focus Shield: Device Owner Active" else "Focus Shield: Standby"
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, if (KioskManager.isDeviceOwner(this)) R.color.accent_green else R.color.accent))
            tvAdminBadge.text = if (KioskManager.isDeviceOwner(this)) "DEVICE OWNER" else "READY"
            tvAdminBadge.setTextColor(ContextCompat.getColor(this, if (KioskManager.isDeviceOwner(this)) R.color.accent_green else R.color.accent))
            scrollDurationPresets.visibility = View.VISIBLE
            tvTimerDisplay.text = "Focus: ${formatDurationLabel(selectedDurationMinutes)}"
            tvTimerSubtext.text = if (KioskManager.isDeviceOwner(this)) "Full OS-level restrictions active. Select duration to begin." else "Select study duration and lock distractions"
            btnKioskToggle.isEnabled = true
            btnKioskToggle.alpha = 1.0f
            btnKioskToggle.text = "Start ${formatDurationLabel(selectedDurationMinutes)}"
            btnKioskToggle.setIconResource(R.drawable.ic_lock)
            btnKioskToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnKioskToggle.strokeWidth = 0

            btnAllowedApps.isEnabled = true
            btnAllowedApps.alpha = 1.0f
            btnAllowedApps.text = "Apps"
            btnAllowedApps.setIconResource(R.drawable.ic_apps)

            btnSwitchHome.isEnabled = true
            btnSwitchHome.alpha = 1.0f
            btnHomeSettings.isEnabled = true
            btnHomeSettings.alpha = 1.0f
            applyFocusDrawerState(animate = false)
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
                tvTimerDisplay.text = "$formatted remaining"
            }

            override fun onFinish() {
                KioskManager.stopKioskMode(this@MainActivity)
                updateProtectionCardStatus()
                layoutSessionComplete.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Focus session completed! Great study session.", Toast.LENGTH_LONG).show()
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
            layoutSessionComplete.visibility = View.GONE
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

    private fun showLauncherSelectionDialog() {
        val pm = packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val launchers = resolveInfos.map { info ->
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            val isCurrent = (pkg == packageName)
            Triple(label, pkg, isCurrent)
        }

        val items = launchers.map { (label, pkg, isCurrent) ->
            if (isCurrent) "$label (Current Default)" else label
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Home Launcher Selection")
            .setItems(items) { _, which ->
                val selected = launchers[which]
                if (selected.second == packageName) {
                    requestSetDefaultLauncher()
                } else {
                    val launchIntent = pm.getLaunchIntentForPackage(selected.second)
                        ?: Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            `package` = selected.second
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    try {
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .setPositiveButton("Change System Default") { _, _ ->
                requestSetDefaultLauncher()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    try {
                        roleRequestLauncher.launch(intent)
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        LauncherHelper.openHomeSettings(this)
    }

    private fun showSetDefaultLauncherPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Set Default Launcher")
            .setMessage("To ensure complete focus and prevent navigation glitching, set GateFocus as your default Home launcher.\n\nYou can easily switch back to Samsung Home whenever not in a focus session using the button in the top bar.")
            .setPositiveButton("Set Home") { _, _ ->
                requestSetDefaultLauncher()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showAccessibilityPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Enable App Blocker")
            .setMessage("FocusGuardian accessibility service is required to block Settings and distracting apps during focus sessions.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showAllowedAppsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val currentAllowed = KioskManager.getAllowedApps(this@MainActivity)

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }

            val appEntries = apps.map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                Pair(pkg, label)
            }.sortedBy { it.second.lowercase() }

            val appNames = appEntries.map { it.second }.toTypedArray()
            val checked = BooleanArray(appEntries.size) { i ->
                currentAllowed.contains(appEntries[i].first)
            }

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Manage Allowed Apps")
                    .setMultiChoiceItems(appNames, checked) { _, which, isChecked ->
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
                when {
                    hideCompleted && allVideos.isNotEmpty() && searchQuery.isEmpty() ->
                        "All lectures completed in this view!"
                    searchQuery.isNotEmpty() || selectedSubject != "All" ->
                        "No matching lectures found"
                    else -> "No video lectures found in /GATE/"
                }
            } else {
                if (searchQuery.isNotEmpty() || selectedSubject != "All") "No matching workbooks found"
                else "No PDF workbooks found in /GATE/"
            }
        }
    }

    private fun updateStudyStats() {
        tvStatsTimeToday.text = progressManager.getTodayStudyFormatted()
        val scopeVideos = if (selectedSubject == "All") {
            allVideos
        } else {
            allVideos.filter { it.playlistName.equals(selectedSubject, ignoreCase = true) }
        }
        val completedCount = scopeVideos.count { it.isCompleted }
        val totalCount = scopeVideos.size
        tvStatsCompleted.text = if (selectedSubject == "All") {
            "$completedCount Done"
        } else {
            "$completedCount / $totalCount"
        }
        val streak = progressManager.getStudyStreak()
        tvStatsStreak.text = "$streak ${if (streak == 1) "Day" else "Days"}"
    }

    private val GENERIC_SUBFOLDERS = setOf(
        "workbooks", "workbook", "books", "book", "notes", "material", "materials",
        "pdf", "pdfs", "lectures", "lecture", "videos", "video", "classes"
    )

    private fun resolveSubjectName(file: File, rootDir: File): String {
        val parent = file.parentFile ?: return "General"
        if (parent.absolutePath.equals(rootDir.absolutePath, ignoreCase = true)) {
            return "General"
        }

        val parentName = parent.name
        if (parentName.lowercase() in GENERIC_SUBFOLDERS) {
            val grandParent = parent.parentFile
            if (grandParent != null && !grandParent.absolutePath.equals(rootDir.absolutePath, ignoreCase = true) && grandParent.name.isNotBlank()) {
                return grandParent.name
            }
        }

        return parentName
    }

    private fun setupSubjectChips() {
        val subjects = linkedSetOf("All")
        for (v in allVideos) {
            if (v.playlistName.isNotBlank() && v.playlistName != "Lectures" && v.playlistName != "General") {
                subjects.add(v.playlistName)
            }
        }
        for (p in allPdfs) {
            if (p.folderName.isNotBlank() && p.folderName != "Workbooks" && p.folderName != "General") {
                subjects.add(p.folderName)
            }
        }

        chipGroupSubjects.removeAllViews()
        for (subject in subjects) {
            val chip = Chip(this).apply {
                text = subject
                isCheckable = true
                isChecked = (subject == selectedSubject)
                textSize = 12f
                chipCornerRadius = 48f
                chipStrokeWidth = 0f
                if (isChecked) {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary_dark))
                    setChipBackgroundColorResource(R.color.accent)
                } else {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(0xFF141D2E.toInt())
                }
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSubject = subject
                        setupSubjectChips()
                        applyFilter()
                    } else if (selectedSubject == subject && chipGroupSubjects.checkedChipId == View.NO_ID) {
                        selectedSubject = "All"
                        setupSubjectChips()
                        applyFilter()
                    }
                }
            }
            chipGroupSubjects.addView(chip)
        }
    }

    private fun openVideoLecture(video: VideoItem) {
        progressManager.saveLastPlayedVideo(video.file.absolutePath)
        val sameSubjectVideos = allVideos.filter {
            it.playlistName.equals(video.playlistName, ignoreCase = true)
        }.ifEmpty { listOf(video) }
        val startIndex = sameSubjectVideos.indexOfFirst {
            it.file.absolutePath == video.file.absolutePath
        }.coerceAtLeast(0)
        val paths = ArrayList(sameSubjectVideos.map { it.file.absolutePath })
        val titles = ArrayList(sameSubjectVideos.map { TitleFormatter.cleanLectureTitle(it.title) })
        VideoPlayerActivity.startPlaylist(
            context = this,
            paths = paths,
            titles = titles,
            startIndex = startIndex
        )
    }

    private fun showVideoCompletionDialog(video: VideoItem) {
        val cleanTitle = TitleFormatter.cleanLectureTitle(video.title)
        val actionText = if (video.isCompleted) "Mark as Incomplete" else "Mark as Completed"
        AlertDialog.Builder(this)
            .setTitle(cleanTitle)
            .setMessage("Change completion status for this lecture?")
            .setPositiveButton(actionText) { _, _ ->
                val newDone = !video.isCompleted
                progressManager.setVideoCompleted(video.file.absolutePath, newDone)
                video.isCompleted = newDone
                if (!newDone) {
                    video.progressMs = progressManager.getVideoProgress(video.file.absolutePath)
                }
                applyFilter()
                updateStudyStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUpNextCard(subjectVideos: List<VideoItem>) {
        val lastPath = progressManager.getLastPlayedVideo()
        val lastPlayedMatch = if (!lastPath.isNullOrEmpty()) {
            subjectVideos.firstOrNull { it.file.absolutePath == lastPath && !it.isCompleted }
        } else null

        val inProgressFallback = subjectVideos.firstOrNull { it.progressMs > 0 && !it.isCompleted }
        val firstUnwatched = subjectVideos.firstOrNull { !it.isCompleted }

        upNextVideo = lastPlayedMatch ?: inProgressFallback ?: firstUnwatched
        updateUpNextVisibility()
    }

    private fun updateUpNextVisibility() {
        val candidate = upNextVideo
        if (tabLayout.selectedTabPosition == 0 && candidate != null && searchQuery.isBlank()) {
            val isResuming = candidate.progressMs > 0
            tvUpNextLabel.text = if (isResuming) "CONTINUE • ${candidate.playlistName}" else "UP NEXT • ${candidate.playlistName}"
            tvUpNextTitle.text = TitleFormatter.cleanLectureTitle(candidate.title)
            btnUpNextPlay.text = if (isResuming) "Resume" else "Start"
            cardUpNext.visibility = View.VISIBLE
        } else {
            cardUpNext.visibility = View.GONE
        }
    }

    private fun applyFilter() {
        val q = searchQuery.trim().lowercase()

        val subjectVideos = allVideos.filter { video ->
            selectedSubject == "All" || video.playlistName.equals(selectedSubject, ignoreCase = true)
        }

        val filteredVideos = subjectVideos.filter { video ->
            val matchesHideDone = !hideCompleted || !video.isCompleted
            val cleanTitle = TitleFormatter.cleanLectureTitle(video.title).lowercase()
            val matchesQuery = q.isEmpty() || cleanTitle.contains(q) ||
                video.title.lowercase().contains(q) || video.playlistName.lowercase().contains(q)
            matchesHideDone && matchesQuery
        }

        val filteredPdfs = allPdfs.filter { pdf ->
            val matchesSubject = (selectedSubject == "All" || pdf.folderName.equals(selectedSubject, ignoreCase = true))
            val matchesQuery = q.isEmpty() || pdf.title.lowercase().contains(q) || pdf.folderName.lowercase().contains(q)
            matchesSubject && matchesQuery
        }

        videoList.clear()
        videoList.addAll(filteredVideos)
        rvVideos.adapter?.notifyDataSetChanged()

        pdfList.clear()
        pdfList.addAll(filteredPdfs)
        rvPdfs.adapter?.notifyDataSetChanged()

        val videoTab = tabLayout.getTabAt(0)
        val pdfTab = tabLayout.getTabAt(1)
        videoTab?.text = "Video Lectures (${filteredVideos.size})"
        pdfTab?.text = "PDF Workbooks (${filteredPdfs.size})"

        updateUpNextCard(subjectVideos)
        updateStudyStats()

        if (tabLayout.selectedTabPosition == 0) {
            checkEmptyState(videoList.isEmpty())
        } else {
            checkEmptyState(pdfList.isEmpty())
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
                scanDirectory(dir, dir, videos, pdfs)
            }

            // Natural alphanumeric sort (e.g. 01, 02, 10...)
            videos.sortWith { v1, v2 ->
                val folderCmp = NaturalOrderComparator.compare(v1.playlistName, v2.playlistName)
                if (folderCmp != 0) folderCmp
                else NaturalOrderComparator.compare(v1.file.name, v2.file.name)
            }

            pdfs.sortWith { p1, p2 ->
                val folderCmp = NaturalOrderComparator.compare(p1.folderName, p2.folderName)
                if (folderCmp != 0) folderCmp
                else NaturalOrderComparator.compare(p1.file.name, p2.file.name)
            }

            withContext(Dispatchers.Main) {
                allVideos.clear()
                allVideos.addAll(videos)

                allPdfs.clear()
                allPdfs.addAll(pdfs)

                setupSubjectChips()
                applyFilter()
                updateStudyStats()
            }
        }
    }

    private fun scanDirectory(rootDir: File, currentDir: File, videos: MutableList<VideoItem>, pdfs: MutableList<PdfItem>) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(rootDir, file, videos, pdfs)
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

                    val playlistName = resolveSubjectName(file, rootDir)
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
                    val folderName = resolveSubjectName(file, rootDir)
                    val item = PdfItem(
                        file = file,
                        title = file.nameWithoutExtension,
                        folderName = folderName,
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

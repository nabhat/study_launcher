package com.gatefocus.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gatefocus.launcher.data.StudyProgressManager
import com.gatefocus.launcher.data.TitleFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class VideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressManager: StudyProgressManager
    private lateinit var tvSeekLeft: TextView
    private lateinit var tvSeekRight: TextView

    // Player HUD & Overlay Controls
    private lateinit var tvSpeedBoostPill: TextView
    private lateinit var layoutLectureComplete: MaterialCardView
    private lateinit var tvCompleteLectureTitle: TextView
    private lateinit var btnNextLecture: MaterialButton
    private lateinit var btnReplayLecture: MaterialButton
    private lateinit var btnFinishPlayer: MaterialButton

    private lateinit var tvTitle: TextView
    private lateinit var btnSpeed: Button
    private lateinit var btnPlayPause: ImageButton

    // VLC Gesture HUD Elements
    private lateinit var layoutBrightnessIndicator: LinearLayout
    private lateinit var ivBrightnessIcon: ImageView
    private lateinit var pbBrightness: ProgressBar
    private lateinit var tvBrightnessPercent: TextView

    private lateinit var layoutVolumeIndicator: LinearLayout
    private lateinit var ivVolumeIcon: ImageView
    private lateinit var pbVolume: ProgressBar
    private lateinit var tvVolumePercent: TextView

    private lateinit var audioManager: AudioManager
    private var maxVolume = 15

    private var videoPath: String = ""
    private var videoTitle: String = "Lecture"
    private var playlistPaths: ArrayList<String> = arrayListOf()
    private var playlistTitles: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = 0

    private var currentSpeedIndex = 0
    private val speeds = arrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "1.75x", "2.0x")

    private var isZoomFill = false
    private var isSpeedBoostActive = false

    private var lastSeekDirection = 0 // -1 left, 1 right
    private var accumulatedSeekSeconds = 0
    private var lastDoubleTapTimeMs = 0L

    private var activePlaybackStartMs = 0L

    private val seekHandler = Handler(Looper.getMainLooper())
    private var seekRunnable: Runnable? = null

    private val hudHandler = Handler(Looper.getMainLooper())
    private var hudRunnable: Runnable? = null

    private val pillHandler = Handler(Looper.getMainLooper())
    private var pillRunnable: Runnable? = null

    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_PLAYLIST_PATHS = "extra_playlist_paths"
        const val EXTRA_PLAYLIST_TITLES = "extra_playlist_titles"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"

        fun start(context: Context, filePath: String, title: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_PATH, filePath)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }

        fun startPlaylist(
            context: Context,
            paths: ArrayList<String>,
            titles: ArrayList<String>,
            startIndex: Int
        ) {
            val safeIdx = startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0))
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_PATH, paths.getOrElse(safeIdx) { "" })
                putExtra(EXTRA_VIDEO_TITLE, titles.getOrElse(safeIdx) { "Lecture" })
                putStringArrayListExtra(EXTRA_PLAYLIST_PATHS, paths)
                putStringArrayListExtra(EXTRA_PLAYLIST_TITLES, titles)
                putExtra(EXTRA_CURRENT_INDEX, safeIdx)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_player)
        enableImmersiveFullscreen()

        progressManager = StudyProgressManager(this)
        currentSpeedIndex = progressManager.getPreferredSpeedIndex().coerceIn(0, speeds.lastIndex)

        playlistPaths = intent.getStringArrayListExtra(EXTRA_PLAYLIST_PATHS) ?: arrayListOf()
        playlistTitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_TITLES) ?: arrayListOf()
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)

        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
        val rawTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Lecture"
        videoTitle = TitleFormatter.cleanLectureTitle(rawTitle)

        playerView = findViewById(R.id.playerView)
        playerView.controllerShowTimeoutMs = 3000
        tvSeekLeft = findViewById(R.id.tvSeekLeft)
        tvSeekRight = findViewById(R.id.tvSeekRight)
        tvSpeedBoostPill = findViewById(R.id.tvSpeedBoostPill)
        layoutLectureComplete = findViewById(R.id.layoutLectureComplete)
        tvCompleteLectureTitle = findViewById(R.id.tvCompleteLectureTitle)
        btnNextLecture = findViewById(R.id.btnNextLecture)
        btnReplayLecture = findViewById(R.id.btnReplayLecture)
        btnFinishPlayer = findViewById(R.id.btnFinishPlayer)

        // VLC Gesture HUD Elements
        layoutBrightnessIndicator = findViewById(R.id.layoutBrightnessIndicator)
        ivBrightnessIcon = findViewById(R.id.ivBrightnessIcon)
        pbBrightness = findViewById(R.id.pbBrightness)
        tvBrightnessPercent = findViewById(R.id.tvBrightnessPercent)

        layoutVolumeIndicator = findViewById(R.id.layoutVolumeIndicator)
        ivVolumeIcon = findViewById(R.id.ivVolumeIcon)
        pbVolume = findViewById(R.id.pbVolume)
        tvVolumePercent = findViewById(R.id.tvVolumePercent)

        // Setup custom controller buttons
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        tvTitle = findViewById(R.id.tvVideoTitle)
        btnSpeed = findViewById(R.id.btnSpeed)
        val btnRew = findViewById<ImageButton>(R.id.exo_rew)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        val btnFfwd = findViewById<ImageButton>(R.id.exo_ffwd)
        val btnBluetooth = findViewById<ImageButton>(R.id.btnBluetooth)

        btnBack?.setOnClickListener { finish() }
        tvTitle.text = videoTitle
        btnSpeed.text = speedLabels[currentSpeedIndex]

        btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            progressManager.savePreferredSpeedIndex(currentSpeedIndex)
            val selectedSpeed = speeds[currentSpeedIndex]
            player?.playbackParameters = PlaybackParameters(selectedSpeed)
            btnSpeed.text = speedLabels[currentSpeedIndex]
        }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        btnRew?.setOnClickListener { rewind10s() }
        btnFfwd?.setOnClickListener { forward10s() }

        btnBluetooth?.setOnClickListener {
            BluetoothDialogHelper.show(this)
        }

        btnNextLecture.setOnClickListener {
            val nextIdx = currentIndex + 1
            if (nextIdx < playlistPaths.size) {
                playLectureAtIndex(nextIdx)
            }
        }

        btnReplayLecture.setOnClickListener {
            layoutLectureComplete.visibility = View.GONE
            player?.let { exo ->
                exo.seekTo(0L)
                exo.playWhenReady = true
                exo.play()
            }
        }

        btnFinishPlayer.setOnClickListener { finish() }

        setupGestures()
        initializePlayer()
    }

    private fun enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    private fun showTopPill(label: String, autoHideMs: Long = 900L) {
        pillRunnable?.let { pillHandler.removeCallbacks(it) }
        tvSpeedBoostPill.text = label
        tvSpeedBoostPill.alpha = 1.0f
        tvSpeedBoostPill.visibility = View.VISIBLE
        if (autoHideMs > 0L) {
            val runnable = Runnable {
                tvSpeedBoostPill.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { tvSpeedBoostPill.visibility = View.GONE }
                    .start()
            }
            pillRunnable = runnable
            pillHandler.postDelayed(runnable, autoHideMs)
        }
    }

    private fun hideTopPill() {
        pillRunnable?.let { pillHandler.removeCallbacks(it) }
        tvSpeedBoostPill.visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var startX = 0f
        var startY = 0f
        var hasScrolled = false
        var isAdjustingBrightness = false
        var isAdjustingVolume = false
        var initialBrightness = 0.5f
        var initialVolumeFraction = 0.5f
        var pinchHandled = false

        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    pinchHandled = false
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (pinchHandled || layoutLectureComplete.visibility == View.VISIBLE) return true
                    val factor = detector.scaleFactor
                    if (factor > 1.12f) {
                        pinchHandled = true
                        if (!isZoomFill) {
                            isZoomFill = true
                            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        showTopPill("Zoom: Fill")
                    } else if (factor < 0.88f) {
                        pinchHandled = true
                        if (isZoomFill) {
                            isZoomFill = false
                            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        showTopPill("Fit to Screen")
                    }
                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (layoutLectureComplete.visibility == View.VISIBLE) return true

                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (layoutLectureComplete.visibility == View.VISIBLE) return true
                val width = playerView.width
                if (width > 0) {
                    if (e.x < width / 2) {
                        rewind10s()
                    } else {
                        forward10s()
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (hasScrolled || scaleGestureDetector.isInProgress || layoutLectureComplete.visibility == View.VISIBLE) return
                val exo = player ?: return
                if (!exo.isPlaying) return
                isSpeedBoostActive = true
                exo.playbackParameters = PlaybackParameters(2.0f)
                showTopPill("2.0x Speed", autoHideMs = 0L)
            }
        })

        playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (scaleGestureDetector.isInProgress || event.pointerCount > 1) {
                hasScrolled = false
                isAdjustingBrightness = false
                isAdjustingVolume = false
                return@setOnTouchListener true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    hasScrolled = false
                    isAdjustingBrightness = false
                    isAdjustingVolume = false

                    val winBrightness = window.attributes.screenBrightness
                    initialBrightness = if (winBrightness >= 0f) {
                        winBrightness
                    } else {
                        try {
                            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255.0f
                        } catch (e: Exception) {
                            0.5f
                        }
                    }.coerceIn(0.01f, 1.0f)

                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    initialVolumeFraction = (currentVol.toFloat() / maxVolume).coerceIn(0f, 1f)

                    gestureDetector.onTouchEvent(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isSpeedBoostActive) {
                        return@setOnTouchListener true
                    }

                    val deltaX = event.x - startX
                    val deltaY = event.y - startY

                    if (!hasScrolled) {
                        if (abs(deltaY) > touchSlop * 1.5f && abs(deltaY) > abs(deltaX)) {
                            hasScrolled = true
                            if (startX < playerView.width / 2) {
                                isAdjustingBrightness = true
                            } else {
                                isAdjustingVolume = true
                            }
                        }
                    }

                    if (hasScrolled) {
                        val height = playerView.height.coerceAtLeast(1)
                        val deltaFraction = (-deltaY) / (height * 0.75f)

                        if (isAdjustingBrightness) {
                            val newBrightness = (initialBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                            val lp = window.attributes
                            lp.screenBrightness = newBrightness
                            window.attributes = lp
                            showBrightnessIndicator(newBrightness)
                        } else if (isAdjustingVolume) {
                            val newVolumeFraction = (initialVolumeFraction + deltaFraction).coerceIn(0f, 1f)
                            val targetVol = (newVolumeFraction * maxVolume).roundToInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            showVolumeIndicator(newVolumeFraction, targetVol)
                        }
                        true
                    } else {
                        gestureDetector.onTouchEvent(event)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSpeedBoostActive) {
                        isSpeedBoostActive = false
                        player?.playbackParameters = PlaybackParameters(speeds[currentSpeedIndex])
                        hideTopPill()
                        true
                    } else if (hasScrolled) {
                        scheduleHudFadeOut()
                        hasScrolled = false
                        isAdjustingBrightness = false
                        isAdjustingVolume = false
                        true
                    } else {
                        gestureDetector.onTouchEvent(event)
                    }
                }

                else -> gestureDetector.onTouchEvent(event)
            }
        }
    }

    private fun showBrightnessIndicator(brightness: Float) {
        hudRunnable?.let { hudHandler.removeCallbacks(it) }
        layoutVolumeIndicator.visibility = View.GONE

        val percent = (brightness * 100).roundToInt()
        pbBrightness.progress = percent
        tvBrightnessPercent.text = "$percent%"

        layoutBrightnessIndicator.alpha = 1.0f
        layoutBrightnessIndicator.visibility = View.VISIBLE
    }

    private fun showVolumeIndicator(volumeFraction: Float, currentVolume: Int) {
        hudRunnable?.let { hudHandler.removeCallbacks(it) }
        layoutBrightnessIndicator.visibility = View.GONE

        val percent = (volumeFraction * 100).roundToInt()
        pbVolume.progress = percent
        tvVolumePercent.text = "$percent%"
        ivVolumeIcon.setImageResource(if (currentVolume == 0) R.drawable.ic_volume_mute else R.drawable.ic_volume_up)

        layoutVolumeIndicator.alpha = 1.0f
        layoutVolumeIndicator.visibility = View.VISIBLE
    }

    private fun scheduleHudFadeOut() {
        hudRunnable?.let { hudHandler.removeCallbacks(it) }
        val runnable = Runnable {
            layoutBrightnessIndicator.animate().alpha(0f).setDuration(250).withEndAction {
                layoutBrightnessIndicator.visibility = View.GONE
            }.start()
            layoutVolumeIndicator.animate().alpha(0f).setDuration(250).withEndAction {
                layoutVolumeIndicator.visibility = View.GONE
            }.start()
        }
        hudRunnable = runnable
        hudHandler.postDelayed(runnable, 650L)
    }

    private fun rewind10s() {
        val exo = player ?: return
        val newPos = (exo.currentPosition - 10000L).coerceAtLeast(0L)
        exo.seekTo(newPos)
        recordSeekFeedback(direction = -1, view = tvSeekLeft)
    }

    private fun forward10s() {
        val exo = player ?: return
        val duration = exo.duration
        val target = exo.currentPosition + 10000L
        val newPos = if (duration > 0) target.coerceAtMost(duration) else target
        exo.seekTo(newPos)
        recordSeekFeedback(direction = 1, view = tvSeekRight)
    }

    private fun recordSeekFeedback(direction: Int, view: TextView) {
        val now = System.currentTimeMillis()
        if (direction == lastSeekDirection && now - lastDoubleTapTimeMs <= 900L) {
            accumulatedSeekSeconds += 10
        } else {
            lastSeekDirection = direction
            accumulatedSeekSeconds = 10
        }
        lastDoubleTapTimeMs = now
        val label = if (direction < 0) "-${accumulatedSeekSeconds}s" else "+${accumulatedSeekSeconds}s"
        showSeekFeedback(view, label)
    }

    private fun showSeekFeedback(view: TextView, text: String) {
        seekRunnable?.let { seekHandler.removeCallbacks(it) }
        tvSeekLeft.visibility = View.GONE
        tvSeekRight.visibility = View.GONE

        view.text = text
        view.alpha = 1.0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f
        view.visibility = View.VISIBLE

        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(120)
            .start()

        val runnable = Runnable {
            view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    view.visibility = View.GONE
                    accumulatedSeekSeconds = 0
                }
                .start()
        }
        seekRunnable = runnable
        seekHandler.postDelayed(runnable, 650)
    }

    private fun initializePlayer() {
        if (videoPath.isEmpty()) {
            finish()
            return
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            finish()
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player = exo
        playerView.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    saveCurrentProgress()
                } else if (playbackState == Player.STATE_ENDED) {
                    markCurrentLectureEnded()
                }
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    activePlaybackStartMs = System.currentTimeMillis()
                    layoutLectureComplete.visibility = View.GONE
                } else {
                    trackElapsedStudyTime()
                }
                syncPlayPauseIcon(isPlaying)
                updatePlaybackState()
            }
        })

        setupMediaSession()
        loadLectureIntoPlayer(videoFile, resetIfCompleted = true)
    }

    private fun loadLectureIntoPlayer(videoFile: File, resetIfCompleted: Boolean) {
        val exo = player ?: return
        layoutLectureComplete.visibility = View.GONE
        progressManager.saveLastPlayedVideo(videoFile.absolutePath)

        val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
        exo.setMediaItem(mediaItem)

        val savedPosition = progressManager.getVideoProgress(videoFile.absolutePath)
        val isDone = progressManager.isVideoCompleted(videoFile.absolutePath)
        if (savedPosition > 0 && !(resetIfCompleted && isDone)) {
            exo.seekTo(savedPosition)
        } else {
            exo.seekTo(0L)
        }

        exo.playbackParameters = PlaybackParameters(speeds[currentSpeedIndex])
        exo.prepare()
        exo.playWhenReady = true
        syncPlayPauseIcon(true)
    }

    private fun playLectureAtIndex(index: Int) {
        val nextPath = playlistPaths.getOrNull(index) ?: return
        val nextFile = File(nextPath)
        if (!nextFile.exists()) return

        saveCurrentProgress()
        currentIndex = index
        videoPath = nextPath
        videoTitle = TitleFormatter.cleanLectureTitle(playlistTitles.getOrElse(index) { nextFile.nameWithoutExtension })
        tvTitle.text = videoTitle
        loadLectureIntoPlayer(nextFile, resetIfCompleted = true)
    }

    private fun markCurrentLectureEnded() {
        val exo = player ?: return
        val duration = exo.duration
        if (duration > 0 && videoPath.isNotEmpty()) {
            progressManager.saveVideoProgress(videoPath, duration, duration)
        }
        trackElapsedStudyTime()

        val hasNext = currentIndex + 1 < playlistPaths.size
        tvCompleteLectureTitle.text = videoTitle
        btnNextLecture.visibility = if (hasNext) View.VISIBLE else View.GONE
        playerView.hideController()
        layoutLectureComplete.visibility = View.VISIBLE
    }

    private fun syncPlayPauseIcon(isPlaying: Boolean) {
        if (::btnPlayPause.isInitialized) {
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    private fun setupMediaSession() {
        val session = MediaSession(this, "GateFocusPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    player?.play()
                }

                override fun onPause() {
                    player?.pause()
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                togglePlayPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                player?.play()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                player?.pause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                rewind10s()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                forward10s()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            @Suppress("DEPRECATION")
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
        mediaSession = session
        updatePlaybackState()
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    private fun updatePlaybackState() {
        val exo = player ?: return
        val isPlaying = exo.isPlaying
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                exo.currentPosition,
                exo.playbackParameters.speed
            )
        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    player?.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player?.pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    rewind10s()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    forward10s()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun saveCurrentProgress() {
        val exo = player ?: return
        val current = exo.currentPosition
        val duration = exo.duration
        if (duration > 0 && videoPath.isNotEmpty() && exo.playbackState != Player.STATE_ENDED) {
            progressManager.saveVideoProgress(videoPath, current, duration)
        }
        trackElapsedStudyTime()
    }

    private fun trackElapsedStudyTime() {
        if (activePlaybackStartMs > 0L) {
            val now = System.currentTimeMillis()
            val elapsedSecs = (now - activePlaybackStartMs) / 1000L
            if (elapsedSecs > 5L) {
                progressManager.addStudySeconds(elapsedSecs)
            }
            activePlaybackStartMs = if (player?.isPlaying == true) now else 0L
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        if (player?.isPlaying == true && activePlaybackStartMs == 0L) {
            activePlaybackStartMs = System.currentTimeMillis()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        seekRunnable?.let { seekHandler.removeCallbacks(it) }
        hudRunnable?.let { hudHandler.removeCallbacks(it) }
        pillRunnable?.let { pillHandler.removeCallbacks(it) }
        saveCurrentProgress()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }
}

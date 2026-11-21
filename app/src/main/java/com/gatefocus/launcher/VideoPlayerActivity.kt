package com.gatefocus.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gatefocus.launcher.data.StudyProgressManager
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressManager: StudyProgressManager
    private lateinit var tvSeekLeft: TextView
    private lateinit var tvSeekRight: TextView

    private var videoPath: String = ""
    private var currentSpeedIndex = 0
    private val speeds = arrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "1.75x", "2.0x")

    private val seekHandler = Handler(Looper.getMainLooper())
    private var seekRunnable: Runnable? = null

    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"

        fun start(context: Context, filePath: String, title: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_PATH, filePath)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_player)

        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Lecture"

        progressManager = StudyProgressManager(this)
        playerView = findViewById(R.id.playerView)
        tvSeekLeft = findViewById(R.id.tvSeekLeft)
        tvSeekRight = findViewById(R.id.tvSeekRight)

        // Setup custom controller buttons
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val tvTitle = findViewById<TextView>(R.id.tvVideoTitle)
        val btnSpeed = findViewById<Button>(R.id.btnSpeed)
        val btnRew = findViewById<ImageButton>(R.id.exo_rew)
        val btnFfwd = findViewById<ImageButton>(R.id.exo_ffwd)

        btnBack?.setOnClickListener { finish() }
        tvTitle?.text = videoTitle

        btnSpeed?.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val selectedSpeed = speeds[currentSpeedIndex]
            player?.playbackParameters = PlaybackParameters(selectedSpeed)
            btnSpeed.text = speedLabels[currentSpeedIndex]
        }

        btnRew?.setOnClickListener {
            rewind10s()
        }

        btnFfwd?.setOnClickListener {
            forward10s()
        }

        setupGestures()
        initializePlayer()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
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
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun rewind10s() {
        val exo = player ?: return
        val current = exo.currentPosition
        val newPos = (current - 10000L).coerceAtLeast(0L)
        exo.seekTo(newPos)
        showSeekFeedback(tvSeekLeft, "⏪ 10s")
    }

    private fun forward10s() {
        val exo = player ?: return
        val current = exo.currentPosition
        val duration = exo.duration
        val target = current + 10000L
        val newPos = if (duration > 0) target.coerceAtMost(duration) else target
        exo.seekTo(newPos)
        showSeekFeedback(tvSeekRight, "⏩ 10s")
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
                .withEndAction { view.visibility = View.GONE }
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

        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        player = exo
        playerView.player = exo

        val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
        exo.setMediaItem(mediaItem)

        // Resume from previous timestamp
        val savedPosition = progressManager.getVideoProgress(videoPath)
        if (savedPosition > 0) {
            exo.seekTo(savedPosition)
        }

        exo.prepare()
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    saveCurrentProgress()
                }
            }
        })
    }

    private var sessionStartTimeMs = 0L

    private fun saveCurrentProgress() {
        val exo = player ?: return
        val current = exo.currentPosition
        val duration = exo.duration
        if (duration > 0 && videoPath.isNotEmpty()) {
            progressManager.saveVideoProgress(videoPath, current, duration)
        }
        trackElapsedStudyTime()
    }

    private fun trackElapsedStudyTime() {
        if (sessionStartTimeMs > 0) {
            val elapsedSecs = (System.currentTimeMillis() - sessionStartTimeMs) / 1000L
            if (elapsedSecs > 5) {
                progressManager.addStudySeconds(elapsedSecs)
            }
            sessionStartTimeMs = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        sessionStartTimeMs = System.currentTimeMillis()
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
        saveCurrentProgress()
        player?.release()
        player = null
    }
}

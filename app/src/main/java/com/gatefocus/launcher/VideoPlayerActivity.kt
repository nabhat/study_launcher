package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private var videoPath: String = ""
    private var currentSpeedIndex = 0
    private val speeds = arrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "1.75x", "2.0x")

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Lecture"

        progressManager = StudyProgressManager(this)
        playerView = findViewById(R.id.playerView)

        // Setup custom controller buttons
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val tvTitle = findViewById<TextView>(R.id.tvVideoTitle)
        val btnSpeed = findViewById<Button>(R.id.btnSpeed)

        btnBack?.setOnClickListener { finish() }
        tvTitle?.text = videoTitle

        btnSpeed?.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val selectedSpeed = speeds[currentSpeedIndex]
            player?.playbackParameters = PlaybackParameters(selectedSpeed)
            btnSpeed.text = speedLabels[currentSpeedIndex]
        }

        initializePlayer()
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

        val exo = ExoPlayer.Builder(this).build()
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

    private fun saveCurrentProgress() {
        val exo = player ?: return
        val current = exo.currentPosition
        val duration = exo.duration
        if (duration > 0 && videoPath.isNotEmpty()) {
            progressManager.saveVideoProgress(videoPath, current, duration)
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
        saveCurrentProgress()
        player?.release()
        player = null
    }
}

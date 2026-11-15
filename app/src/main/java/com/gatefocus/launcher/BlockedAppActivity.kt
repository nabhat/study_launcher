package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class BlockedAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
        const val EXTRA_IS_TAMPER_ATTEMPT = "extra_is_tamper_attempt"

        fun start(context: Context, appName: String, isTamperAttempt: Boolean = false) {
            val intent = Intent(context, BlockedAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_BLOCKED_APP_NAME, appName)
                putExtra(EXTRA_IS_TAMPER_ATTEMPT, isTamperAttempt)
            }
            context.startActivity(intent)
        }
    }

    private var sessionTimer: CountDownTimer? = null
    private lateinit var tvBlockedTimer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_app)

        val appName = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Restricted App"
        val isTamper = intent.getBooleanExtra(EXTRA_IS_TAMPER_ATTEMPT, false)

        val tvBlockedBadge = findViewById<TextView>(R.id.tvBlockedBadge)
        val tvBlockedAppName = findViewById<TextView>(R.id.tvBlockedAppName)
        val tvBlockedMessage = findViewById<TextView>(R.id.tvBlockedMessage)
        tvBlockedTimer = findViewById(R.id.tvBlockedTimer)
        val btnReturnToStudy = findViewById<MaterialButton>(R.id.btnReturnToStudy)

        if (isTamper) {
            tvBlockedBadge.text = "TAMPER PROTECTION"
            tvBlockedBadge.setTextColor(ContextCompat.getColor(this, R.color.red))
            tvBlockedAppName.text = "Settings Tamper Blocked"
            tvBlockedMessage.text = "Attempt to modify Device Admin, Accessibility, or App Settings was blocked. Focus Lock remains active."
        } else {
            tvBlockedBadge.text = "FOCUS MODE ACTIVE"
            tvBlockedAppName.text = "$appName is Blocked"
            tvBlockedMessage.text = "This application is restricted during study hours. Stay disciplined and focus on your GATE exam preparation."
        }

        startLiveTimer()

        btnReturnToStudy.setOnClickListener {
            returnToHome()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionTimer?.cancel()
    }

    private fun startLiveTimer() {
        sessionTimer?.cancel()
        val remainingMillis = KioskManager.getRemainingTimeMillis(this)
        if (remainingMillis <= 0L) {
            tvBlockedTimer.text = "Focus session completed."
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
                tvBlockedTimer.text = "⏳ Focus Session Active: $formatted remaining"
            }

            override fun onFinish() {
                tvBlockedTimer.text = "Focus session completed."
                KioskManager.stopKioskMode(this@BlockedAppActivity)
                Toast.makeText(this@BlockedAppActivity, "Focus timer finished! You may now exit.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private fun returnToHome() {
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(homeIntent)
        finish()
    }
}

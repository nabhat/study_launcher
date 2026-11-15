package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_app)

        val appName = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Restricted App"
        val isTamper = intent.getBooleanExtra(EXTRA_IS_TAMPER_ATTEMPT, false)

        val tvBlockedBadge = findViewById<TextView>(R.id.tvBlockedBadge)
        val tvBlockedAppName = findViewById<TextView>(R.id.tvBlockedAppName)
        val tvBlockedMessage = findViewById<TextView>(R.id.tvBlockedMessage)
        val btnReturnToStudy = findViewById<MaterialButton>(R.id.btnReturnToStudy)
        val btnEmergencyUnlock = findViewById<MaterialButton>(R.id.btnEmergencyUnlock)

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

        btnReturnToStudy.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(homeIntent)
            finish()
        }

        btnEmergencyUnlock.setOnClickListener {
            showEmergencyUnlockDialog()
        }
    }

    private fun showEmergencyUnlockDialog() {
        val input = EditText(this).apply {
            hint = "Enter Master PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Emergency Unlock")
            .setMessage("Enter Master PIN to temporarily disable Focus Mode:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val enteredPin = input.text.toString().trim()
                if (enteredPin == KioskManager.getPin(this)) {
                    KioskManager.stopKioskMode(this)
                    Toast.makeText(this, "Focus Mode Temporarily Paused", Toast.LENGTH_SHORT).show()
                    val homeIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(homeIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN. Focus Mode remains active.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

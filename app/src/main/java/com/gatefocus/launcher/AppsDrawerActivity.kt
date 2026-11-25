package com.gatefocus.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawerAppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var isAllowedInFocus: Boolean
)

class AppsDrawerActivity : AppCompatActivity() {

    private lateinit var rvAppsGrid: RecyclerView
    private lateinit var etAppSearch: EditText
    private lateinit var tvAppsTitle: TextView
    private lateinit var tvAppsModeBadge: TextView
    private lateinit var tvAppsHint: TextView
    private lateinit var tvAppsEmpty: TextView

    private val allApps = mutableListOf<DrawerAppEntry>()
    private val filteredApps = mutableListOf<DrawerAppEntry>()
    private var searchQuery: String = ""

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AppsDrawerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_drawer)

        rvAppsGrid = findViewById(R.id.rvAppsGrid)
        etAppSearch = findViewById(R.id.etAppSearch)
        tvAppsTitle = findViewById(R.id.tvAppsTitle)
        tvAppsModeBadge = findViewById(R.id.tvAppsModeBadge)
        tvAppsHint = findViewById(R.id.tvAppsHint)
        tvAppsEmpty = findViewById(R.id.tvAppsEmpty)

        val btnBack = findViewById<MaterialButton>(R.id.btnAppsBack)
        btnBack.setOnClickListener { finish() }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rvAppsGrid.layoutManager = GridLayoutManager(this, if (isLandscape) 6 else 4)
        rvAppsGrid.adapter = AppsGridAdapter(
            apps = filteredApps,
            onAppClick = { entry -> launchApp(entry) },
            onAppLongClick = { entry -> toggleFocusWhitelist(entry) }
        )

        etAppSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                applyAppFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val isFocusActive = KioskManager.isFocusLockActive(this)
        if (isFocusActive) {
            tvAppsTitle.text = "Allowed Study Apps"
            tvAppsModeBadge.text = "FOCUS ACTIVE"
            tvAppsModeBadge.visibility = View.VISIBLE
            tvAppsHint.text = "Focus Lock active • Only your allowed study apps are shown"
        } else {
            tvAppsTitle.text = "Apps"
            tvAppsModeBadge.visibility = View.GONE
            tvAppsHint.text = "Tap to open • Long-press an app to allow/remove during Focus Sessions"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val allowedSet = KioskManager.getAllowedApps(this@AppsDrawerActivity)
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolved = pm.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }

            val loaded = resolved.mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (isFocusActive && !allowedSet.contains(pkg)) {
                    return@mapNotNull null
                }
                DrawerAppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = info.loadIcon(pm),
                    isAllowedInFocus = allowedSet.contains(pkg)
                )
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(loaded)
                applyAppFilter()
            }
        }
    }

    private fun applyAppFilter() {
        val q = searchQuery.trim().lowercase()
        val matching = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase().contains(q) }
        }

        filteredApps.clear()
        filteredApps.addAll(matching)
        rvAppsGrid.adapter?.notifyDataSetChanged()
        tvAppsEmpty.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launchApp(entry: DrawerAppEntry) {
        if (KioskManager.isFocusLockActive(this) && !KioskManager.getAllowedApps(this).contains(entry.packageName)) {
            Toast.makeText(this, "Blocked while Focus Session is active.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open ${entry.label}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Unable to open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFocusWhitelist(entry: DrawerAppEntry) {
        if (KioskManager.isFocusLockActive(this)) {
            Toast.makeText(this, "Whitelist is locked during an active Focus Session.", Toast.LENGTH_SHORT).show()
            return
        }
        if (FocusGuardianService.isSettingsPackage(entry.packageName)) {
            Toast.makeText(this, "Settings cannot be allowed during Focus Sessions.", Toast.LENGTH_SHORT).show()
            return
        }

        val actionLabel = if (entry.isAllowedInFocus) "Remove from Focus Apps" else "Allow During Focus"
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setMessage(
                if (entry.isAllowedInFocus) "Remove ${entry.label} from your allowed Focus Session apps?"
                else "Allow opening ${entry.label} during Focus Sessions?"
            )
            .setPositiveButton(actionLabel) { _, _ ->
                if (entry.isAllowedInFocus) {
                    KioskManager.removeAllowedApp(this, entry.packageName)
                } else {
                    KioskManager.addAllowedApp(this, entry.packageName)
                }
                entry.isAllowedInFocus = !entry.isAllowedInFocus
                rvAppsGrid.adapter?.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private class AppsGridAdapter(
    private val apps: List<DrawerAppEntry>,
    private val onAppClick: (DrawerAppEntry) -> Unit,
    private val onAppLongClick: (DrawerAppEntry) -> Unit
) : RecyclerView.Adapter<AppsGridAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppLabel: TextView = view.findViewById(R.id.tvAppLabel)
        val tvAppFocusBadge: TextView = view.findViewById(R.id.tvAppFocusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_drawer, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val entry = apps[position]
        holder.ivAppIcon.setImageDrawable(entry.icon)
        holder.tvAppLabel.text = entry.label
        holder.tvAppFocusBadge.visibility = if (entry.isAllowedInFocus) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onAppClick(entry) }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(entry)
            true
        }
    }

    override fun getItemCount(): Int = apps.size
}

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
    val icon: Drawable
)

class AppsDrawerActivity : AppCompatActivity() {

    private lateinit var rvAppsGrid: RecyclerView
    private lateinit var etAppSearch: EditText
    private lateinit var tvAppsEmpty: TextView

    private val allApps = mutableListOf<DrawerAppEntry>()
    private val filteredApps = mutableListOf<DrawerAppEntry>()
    private var searchQuery: String = ""

    companion object {
        fun start(context: Context) {
            if (KioskManager.isFocusLockActive(context)) {
                Toast.makeText(context, "Apps list is locked while Focus Session is active.", Toast.LENGTH_SHORT).show()
                return
            }
            context.startActivity(Intent(context, AppsDrawerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (KioskManager.isFocusLockActive(this)) {
            Toast.makeText(this, "Apps list is locked while Focus Session is active.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_apps_drawer)

        rvAppsGrid = findViewById(R.id.rvAppsGrid)
        etAppSearch = findViewById(R.id.etAppSearch)
        tvAppsEmpty = findViewById(R.id.tvAppsEmpty)

        val btnBack = findViewById<MaterialButton>(R.id.btnAppsBack)
        btnBack.setOnClickListener { finish() }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rvAppsGrid.layoutManager = GridLayoutManager(this, if (isLandscape) 6 else 4)
        rvAppsGrid.adapter = AppsGridAdapter(
            apps = filteredApps,
            onAppClick = { entry -> launchApp(entry) }
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
        if (KioskManager.isFocusLockActive(this)) {
            finish()
            return
        }
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolved = pm.queryIntentActivities(mainIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }

            val loaded = resolved.map { info ->
                DrawerAppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = info.loadIcon(pm)
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
        if (KioskManager.isFocusLockActive(this)) {
            Toast.makeText(this, "Apps list is locked while Focus Session is active.", Toast.LENGTH_SHORT).show()
            finish()
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
}

private class AppsGridAdapter(
    private val apps: List<DrawerAppEntry>,
    private val onAppClick: (DrawerAppEntry) -> Unit
) : RecyclerView.Adapter<AppsGridAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppLabel: TextView = view.findViewById(R.id.tvAppLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_drawer, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val entry = apps[position]
        holder.ivAppIcon.setImageDrawable(entry.icon)
        holder.tvAppLabel.text = entry.label
        holder.itemView.setOnClickListener { onAppClick(entry) }
    }

    override fun getItemCount(): Int = apps.size
}

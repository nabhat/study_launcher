package com.gatefocus.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

@SuppressLint("MissingPermission")
object BluetoothDialogHelper {

    data class BtItem(
        val device: BluetoothDevice,
        val name: String,
        val address: String,
        var isConnected: Boolean,
        var isConnecting: Boolean,
        val isBonded: Boolean
    )

    fun show(activity: Activity) {
        // Check Bluetooth permissions on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnect || !hasScan) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    101
                )
                Toast.makeText(activity, "Grant Bluetooth permissions to connect headphones", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            Toast.makeText(activity, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_bluetooth, null)
        dialog.setContentView(view)

        val btnClose = view.findViewById<MaterialButton>(R.id.btnBtClose)
        val layoutBtOff = view.findViewById<LinearLayout>(R.id.layoutBtOff)
        val btnEnableBt = view.findViewById<MaterialButton>(R.id.btnEnableBt)

        val rvPaired = view.findViewById<RecyclerView>(R.id.rvPairedDevices)
        val tvNoPaired = view.findViewById<TextView>(R.id.tvNoPairedDevices)

        val btnScan = view.findViewById<MaterialButton>(R.id.btnScanDevices)
        val pbScanning = view.findViewById<ProgressBar>(R.id.pbScanning)
        val rvNearby = view.findViewById<RecyclerView>(R.id.rvNearbyDevices)
        val tvNoNearby = view.findViewById<TextView>(R.id.tvNoNearbyDevices)

        rvPaired.layoutManager = LinearLayoutManager(activity)
        rvNearby.layoutManager = LinearLayoutManager(activity)

        val pairedList = mutableListOf<BtItem>()
        val nearbyList = mutableListOf<BtItem>()

        var a2dpProxy: BluetoothProfile? = null
        var headsetProxy: BluetoothProfile? = null

        fun isConnectedAudio(device: BluetoothDevice): Boolean {
            val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val dName = getDeviceName(device)
            val isAudioActive = outputs.any {
                (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                 it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                 it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) &&
                (it.productName.toString().equals(dName, ignoreCase = true) || dName.contains(it.productName.toString(), ignoreCase = true))
            }
            if (isAudioActive) return true

            // Also check proxy connected devices
            val a2dpConnected = a2dpProxy?.getConnectedDevices()?.contains(device) == true
            val headsetConnected = headsetProxy?.getConnectedDevices()?.contains(device) == true
            return a2dpConnected || headsetConnected
        }

        lateinit var pairedAdapter: BtDeviceAdapter
        lateinit var nearbyAdapter: BtDeviceAdapter

        fun refreshPairedList() {
            pairedList.clear()
            if (adapter.isEnabled) {
                layoutBtOff.visibility = View.GONE
                val bonded = try {
                    adapter.bondedDevices ?: emptySet()
                } catch (e: Exception) {
                    emptySet()
                }

                for (dev in bonded) {
                    val name = getDeviceName(dev)
                    val connected = isConnectedAudio(dev)
                    pairedList.add(BtItem(dev, name, dev.address, connected, isConnecting = false, isBonded = true))
                }
            } else {
                layoutBtOff.visibility = View.VISIBLE
            }

            pairedAdapter.notifyDataSetChanged()
            tvNoPaired.visibility = if (pairedList.isEmpty() && adapter.isEnabled) View.VISIBLE else View.GONE
        }

        fun connectDevice(item: BtItem) {
            item.isConnecting = true
            pairedAdapter.notifyDataSetChanged()
            nearbyAdapter.notifyDataSetChanged()

            val dev = item.device
            Toast.makeText(activity, "Connecting to ${item.name}...", Toast.LENGTH_SHORT).show()

            // Invoke A2DP connect
            a2dpProxy?.let { proxy ->
                try {
                    val m = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    m.isAccessible = true
                    m.invoke(proxy, dev)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Invoke Headset connect
            headsetProxy?.let { proxy ->
                try {
                    val m = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    m.isAccessible = true
                    m.invoke(proxy, dev)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Refresh after short delay
            view.postDelayed({
                item.isConnecting = false
                refreshPairedList()
            }, 3000L)
        }

        fun disconnectDevice(item: BtItem) {
            val dev = item.device
            Toast.makeText(activity, "Disconnecting ${item.name}...", Toast.LENGTH_SHORT).show()

            a2dpProxy?.let { proxy ->
                try {
                    val m = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    m.isAccessible = true
                    m.invoke(proxy, dev)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            headsetProxy?.let { proxy ->
                try {
                    val m = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    m.isAccessible = true
                    m.invoke(proxy, dev)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            view.postDelayed({
                refreshPairedList()
            }, 1500L)
        }

        fun pairDevice(item: BtItem) {
            item.isConnecting = true
            nearbyAdapter.notifyDataSetChanged()
            Toast.makeText(activity, "Pairing with ${item.name}...", Toast.LENGTH_SHORT).show()

            try {
                item.device.createBond()
            } catch (e: Exception) {
                Toast.makeText(activity, "Failed to initiate pairing: ${e.message}", Toast.LENGTH_SHORT).show()
                item.isConnecting = false
                nearbyAdapter.notifyDataSetChanged()
            }
        }

        pairedAdapter = BtDeviceAdapter(pairedList, onConnect = { connectDevice(it) }, onDisconnect = { disconnectDevice(it) }, onPair = { pairDevice(it) })
        nearbyAdapter = BtDeviceAdapter(nearbyList, onConnect = { connectDevice(it) }, onDisconnect = { disconnectDevice(it) }, onPair = { pairDevice(it) })

        rvPaired.adapter = pairedAdapter
        rvNearby.adapter = nearbyAdapter

        // Connect profile proxies to query active states
        adapter.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy = proxy
                    refreshPairedList()
                } else if (profile == BluetoothProfile.HEADSET) {
                    headsetProxy = proxy
                    refreshPairedList()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) a2dpProxy = null
                else if (profile == BluetoothProfile.HEADSET) headsetProxy = null
            }
        }, BluetoothProfile.A2DP)

        adapter.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    headsetProxy = proxy
                    refreshPairedList()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HEADSET) headsetProxy = null
            }
        }, BluetoothProfile.HEADSET)

        btnEnableBt.setOnClickListener {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivity(enableIntent)
        }

        btnScan.setOnClickListener {
            if (!adapter.isEnabled) {
                Toast.makeText(activity, "Please turn on Bluetooth first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            nearbyList.clear()
            nearbyAdapter.notifyDataSetChanged()
            tvNoNearby.text = "Scanning for nearby headphones..."
            tvNoNearby.visibility = View.VISIBLE
            pbScanning.visibility = View.VISIBLE

            adapter.startDiscovery()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // BroadcastReceiver for Bluetooth changes and scanning
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        refreshPairedList()
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (dev != null) {
                            val name = getDeviceName(dev)
                            // Filter out unnamed or already paired
                            if (name.isNotBlank() && name != "Unknown Device" && !pairedList.any { it.address == dev.address }) {
                                if (!nearbyList.any { it.address == dev.address }) {
                                    nearbyList.add(BtItem(dev, name, dev.address, isConnected = false, isConnecting = false, isBonded = false))
                                    nearbyAdapter.notifyDataSetChanged()
                                    tvNoNearby.visibility = View.GONE
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        pbScanning.visibility = View.VISIBLE
                        btnScan.isEnabled = false
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        pbScanning.visibility = View.GONE
                        btnScan.isEnabled = true
                        if (nearbyList.isEmpty()) {
                            tvNoNearby.text = "No nearby devices found. Make sure headphones are in pairing mode."
                            tvNoNearby.visibility = View.VISIBLE
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        if (state == BluetoothDevice.BOND_BONDED) {
                            Toast.makeText(activity, "Paired successfully!", Toast.LENGTH_SHORT).show()
                            refreshPairedList()
                            val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            dev?.let {
                                nearbyList.removeAll { item -> item.address == it.address }
                                nearbyAdapter.notifyDataSetChanged()
                                val pairedItem = pairedList.find { item -> item.address == it.address }
                                if (pairedItem != null) {
                                    connectDevice(pairedItem)
                                }
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        refreshPairedList()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        activity.registerReceiver(receiver, filter)

        dialog.setOnDismissListener {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                activity.unregisterReceiver(receiver)
            } catch (e: Exception) {}

            a2dpProxy?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProxy?.let { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }

        refreshPairedList()
        dialog.show()
    }

    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Bluetooth Device"
        } catch (e: Exception) {
            "Bluetooth Device"
        }
    }

    class BtDeviceAdapter(
        private val items: List<BtItem>,
        private val onConnect: (BtItem) -> Unit,
        private val onDisconnect: (BtItem) -> Unit,
        private val onPair: (BtItem) -> Unit
    ) : RecyclerView.Adapter<BtDeviceAdapter.BtViewHolder>() {

        class BtViewHolder(val v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.ivDeviceIcon)
            val tvName: TextView = v.findViewById(R.id.tvDeviceName)
            val tvStatus: TextView = v.findViewById(R.id.tvDeviceStatus)
            val btnAction: MaterialButton = v.findViewById(R.id.btnDeviceAction)
            val pbAction: ProgressBar = v.findViewById(R.id.pbDeviceAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BtViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bluetooth_device, parent, false)
            return BtViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: BtViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name

            if (item.isConnecting) {
                holder.pbAction.visibility = View.VISIBLE
                holder.btnAction.visibility = View.GONE
                holder.tvStatus.text = if (item.isBonded) "Connecting..." else "Pairing..."
            } else {
                holder.pbAction.visibility = View.GONE
                holder.btnAction.visibility = View.VISIBLE

                if (item.isBonded) {
                    if (item.isConnected) {
                        holder.tvStatus.text = "Connected (Audio)"
                        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.v.context, R.color.accent_green))
                        holder.btnAction.text = "Disconnect"
                        holder.btnAction.setTextColor(ContextCompat.getColor(holder.v.context, R.color.red))
                        holder.btnAction.setOnClickListener { onDisconnect(item) }
                    } else {
                        holder.tvStatus.text = "Saved • Tap to connect"
                        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.v.context, R.color.text_secondary))
                        holder.btnAction.text = "Connect"
                        holder.btnAction.setTextColor(ContextCompat.getColor(holder.v.context, R.color.accent))
                        holder.btnAction.setOnClickListener { onConnect(item) }
                    }
                } else {
                    holder.tvStatus.text = "Nearby device"
                    holder.tvStatus.setTextColor(ContextCompat.getColor(holder.v.context, R.color.text_secondary))
                    holder.btnAction.text = "Pair"
                    holder.btnAction.setTextColor(ContextCompat.getColor(holder.v.context, R.color.accent))
                    holder.btnAction.setOnClickListener { onPair(item) }
                }
            }
        }
    }
}

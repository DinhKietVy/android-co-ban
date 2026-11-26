package com.example.filemanagementapp.profile.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.auth.local.DeviceFingerprintProvider
import com.example.filemanagementapp.data.auth.model.TrustedDeviceDto
import com.example.filemanagementapp.data.auth.network.AuthNetworkModule
import com.example.filemanagementapp.data.auth.repository.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TrustedDevicesActivity : AppCompatActivity() {

    private val authRepository by lazy {
        AuthRepository(
            authApiService = AuthNetworkModule.authApiService,
            gson = AuthNetworkModule.gson
        )
    }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRemoveAll: MaterialButton
    private lateinit var adapter: TrustedDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trusted_devices)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.rvTrustedDevices)
        tvEmpty = findViewById(R.id.tvEmpty)
        progressBar = findViewById(R.id.progressBar)
        btnRemoveAll = findViewById(R.id.btnRemoveAll)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = TrustedDeviceAdapter(
            currentFingerprint = DeviceFingerprintProvider.getFingerprint(),
            onRemoveClick = ::confirmRemoveDevice
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadDevices(showSpinner = false) }
        btnRemoveAll.setOnClickListener { confirmRemoveAllDevices() }

        loadDevices(showSpinner = true)
    }

    private fun loadDevices(showSpinner: Boolean) {
        if (showSpinner) progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            authRepository.getTrustedDevices()
                .onSuccess { devices ->
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    adapter.submitList(devices)
                    val isEmpty = devices.isEmpty()
                    tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    btnRemoveAll.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
                .onFailure { error ->
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this@TrustedDevicesActivity,
                        error.message ?: getString(R.string.trusted_devices_load_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun confirmRemoveDevice(device: TrustedDeviceDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trusted_device_remove_title)
            .setMessage(
                getString(
                    R.string.trusted_device_remove_message,
                    device.device_name ?: getString(R.string.trusted_device_unknown)
                )
            )
            .setPositiveButton(R.string.trusted_device_remove) { _, _ ->
                removeDevice(device.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removeDevice(deviceId: Int) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            authRepository.removeTrustedDevice(deviceId)
                .onSuccess {
                    Toast.makeText(
                        this@TrustedDevicesActivity,
                        R.string.trusted_device_removed,
                        Toast.LENGTH_SHORT
                    ).show()
                    loadDevices(showSpinner = false)
                }
                .onFailure { error ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@TrustedDevicesActivity,
                        error.message ?: getString(R.string.trusted_devices_load_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun confirmRemoveAllDevices() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trusted_devices_remove_all)
            .setMessage(R.string.trusted_devices_remove_all_message)
            .setPositiveButton(R.string.trusted_devices_remove_all) { _, _ ->
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    authRepository.removeAllTrustedDevices()
                        .onSuccess {
                            Toast.makeText(
                                this@TrustedDevicesActivity,
                                R.string.trusted_devices_all_removed,
                                Toast.LENGTH_SHORT
                            ).show()
                            loadDevices(showSpinner = false)
                        }
                        .onFailure { error ->
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@TrustedDevicesActivity,
                                error.message ?: getString(R.string.trusted_devices_load_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private class TrustedDeviceAdapter(
    private val currentFingerprint: String?,
    private val onRemoveClick: (TrustedDeviceDto) -> Unit
) : RecyclerView.Adapter<TrustedDeviceAdapter.ViewHolder>() {

    private val items = mutableListOf<TrustedDeviceDto>()

    fun submitList(newItems: List<TrustedDeviceDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trusted_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.tvDeviceName.text = item.device_name ?: context.getString(R.string.trusted_device_unknown)
        holder.tvDeviceIp.text = context.getString(
            R.string.trusted_device_ip_format,
            item.ip_address ?: "-"
        )
        holder.tvLastUsed.text = context.getString(
            R.string.trusted_device_last_used_format,
            item.last_used_at?.take(19)?.replace("T", " ") ?: "-"
        )
        val isCurrent = !currentFingerprint.isNullOrBlank() &&
            item.fingerprint.equals(currentFingerprint, ignoreCase = true)
        holder.tvCurrentBadge.visibility = if (isCurrent) View.VISIBLE else View.GONE
        holder.btnRemove.setOnClickListener { onRemoveClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvCurrentBadge: TextView = view.findViewById(R.id.tvCurrentBadge)
        val tvDeviceIp: TextView = view.findViewById(R.id.tvDeviceIp)
        val tvLastUsed: TextView = view.findViewById(R.id.tvLastUsed)
        val btnRemove: MaterialButton = view.findViewById(R.id.btnRemoveDevice)
    }
}

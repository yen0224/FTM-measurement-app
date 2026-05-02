package com.example.wififtm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wififtm.databinding.FragmentFtmBinding
import java.util.concurrent.Executors

class FtmFragment : Fragment() {

    private var _binding: FragmentFtmBinding? = null
    private val binding get() = _binding!!

    private lateinit var wifiManager: WifiManager
    private var rttManager: WifiRttManager? = null

    private val apAdapter = AccessPointAdapter(AccessPointAdapter.Mode.SELECT) { _, _ ->
        updateRangingButton()
    }
    private val resultAdapter = FtmResultAdapter()

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var isRanging = false
    private var rangingRunnable: Runnable? = null
    private val bssidToSsid = mutableMapOf<String, String>()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                handleScanResults()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startWifiScan()
        else Toast.makeText(requireContext(), getString(R.string.permission_required), Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFtmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupManagers()
        setupRecyclerViews()
        setupButtons()
        updateDeviceSupportPanel()
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        // Refresh RTT availability (can change at runtime)
        updateRttReadyIndicator()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(scanReceiver)
        stopRanging()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor.shutdown()
        _binding = null
    }

    private fun setupManagers() {
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        rttManager = if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            requireContext().getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager
        } else null
    }

    private fun setupRecyclerViews() {
        binding.rvAccessPoints.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = apAdapter
        }
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultAdapter
        }
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener { requestPermissionsAndScan() }
        binding.btnStartRanging.setOnClickListener {
            if (isRanging) stopRanging() else startRanging()
        }
        binding.btnClear.setOnClickListener {
            resultAdapter.clear()
            updateResultCount()
        }
    }

    // ── Device support panel ─────────────────────────────────────────────────

    private fun updateDeviceSupportPanel() {
        val pm = requireContext().packageManager

        // 802.11mc: hardware feature flag
        val mcSupported = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
        binding.tvMcIcon.text = if (mcSupported) "✓" else "✗"
        binding.tvMcIcon.setTextColor(colorFor(mcSupported))
        binding.tvMcStatus.text = if (mcSupported) "硬體支援" else "不支援"

        // 802.11az: OS-level support requires Android 13+; hardware still needs
        // FEATURE_WIFI_RTT and the driver to implement it. We report "OS 支援"
        // when both conditions are met, since there's no separate feature flag.
        val azOsSupport = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mcSupported
        binding.tvAzIcon.text = if (azOsSupport) "✓" else "✗"
        binding.tvAzIcon.setTextColor(colorFor(azOsSupport))
        binding.tvAzStatus.text = when {
            !mcSupported -> "不支援"
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "OS 未支援 (需 Android 13)"
            else -> "OS 支援 (API 33+)"
        }

        updateRttReadyIndicator()
    }

    private fun updateRttReadyIndicator() {
        val available = rttManager?.isAvailable == true
        binding.tvRttIcon.text = if (available) "✓" else "✗"
        binding.tvRttIcon.setTextColor(colorFor(available))
        binding.tvRttStatus.text = if (available) "可用" else "不可用"
    }

    private fun colorFor(ok: Boolean) = ContextCompat.getColor(
        requireContext(), if (ok) R.color.ios_green else R.color.ios_red
    )

    // ── Scan ─────────────────────────────────────────────────────────────────

    private fun requestPermissionsAndScan() {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startWifiScan()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startWifiScan() {
        updateStatus("掃描中…")
        binding.btnScan.isEnabled = false
        if (!wifiManager.startScan()) handleScanResults()
    }

    @Suppress("DEPRECATION")
    private fun handleScanResults() {
        binding.btnScan.isEnabled = true
        val results = wifiManager.scanResults ?: emptyList()
        results.forEach { bssidToSsid[it.BSSID] = it.SSID }
        apAdapter.updateList(results)
        val rttCount = results.count { it.is80211mcResponder }
        updateStatus("${results.size} 個 AP，$rttCount 個支援 RTT")
        updateRttReadyIndicator()
    }

    // ── Ranging ──────────────────────────────────────────────────────────────

    private fun startRanging() {
        if (!hasRangingPermission()) { requestPermissionsAndScan(); return }
        val selected = apAdapter.getSelectedAPs()
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.select_ap_hint), Toast.LENGTH_SHORT).show()
            return
        }
        if (rttManager?.isAvailable != true) {
            Toast.makeText(requireContext(), getString(R.string.rtt_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        isRanging = true
        binding.btnStartRanging.text = getString(R.string.stop_ranging)
        setButtonTint(binding.btnStartRanging, R.color.ios_red)
        updateStatus("測距中…")
        scheduleNextRanging(selected)
    }

    private fun stopRanging() {
        isRanging = false
        rangingRunnable?.let { handler.removeCallbacks(it) }
        _binding?.let {
            it.btnStartRanging.text = getString(R.string.start_ranging)
            setButtonTint(it.btnStartRanging, R.color.ios_green)
            updateStatus("已停止")
        }
    }

    private fun scheduleNextRanging(aps: List<ScanResult>) {
        val intervalMs = _binding?.etInterval?.text.toString().toLongOrNull() ?: 1000L
        val runnable = Runnable {
            if (!isRanging) return@Runnable
            performRanging(aps)
            if (isRanging) scheduleNextRanging(aps)
        }
        rangingRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }

    private fun performRanging(aps: List<ScanResult>) {
        if (!hasRangingPermission()) return
        val rtt = rttManager ?: return
        try {
            val request = RangingRequest.Builder()
                .apply { aps.forEach { addAccessPoint(it) } }
                .build()
            rtt.startRanging(request, executor, object : RangingResultCallback() {
                override fun onRangingFailure(code: Int) {
                    activity?.runOnUiThread { _binding?.let { updateStatus("測距失敗 (code=$code)") } }
                }
                override fun onRangingResults(results: List<RangingResult>) {
                    val items = results.map { r ->
                        FtmResultItem(r, bssidToSsid[r.macAddress.toString()] ?: r.macAddress.toString())
                    }
                    activity?.runOnUiThread {
                        val b = _binding ?: return@runOnUiThread
                        resultAdapter.addResults(items)
                        updateResultCount()
                        if (b.cbAutoScroll.isChecked) b.rvResults.scrollToPosition(0)
                        val ok = results.count { it.status == RangingResult.STATUS_SUCCESS }
                        updateStatus("測距中 — 上一批 $ok/${results.size} 成功")
                    }
                }
            })
        } catch (e: SecurityException) {
            activity?.runOnUiThread {
                _binding?.let { updateStatus("權限不足: ${e.message}") }
                stopRanging()
            }
        } catch (e: IllegalArgumentException) {
            activity?.runOnUiThread {
                _binding?.let { updateStatus("請求錯誤: ${e.message}") }
                stopRanging()
            }
        }
    }

    private fun hasRangingPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine || nearby
    }

    private fun setButtonTint(btn: com.google.android.material.button.MaterialButton, colorRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun updateRangingButton() {
        binding.btnStartRanging.isEnabled = apAdapter.hasSelection()
    }

    private fun updateStatus(msg: String) { _binding?.tvStatus?.text = msg }
    private fun updateResultCount() { _binding?.tvResultCount?.text = "結果: ${resultAdapter.getCount()}" }
}

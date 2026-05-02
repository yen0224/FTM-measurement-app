package com.example.wififtm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wififtm.databinding.FragmentBeaconBinding

class BeaconFragment : Fragment() {

    private var _binding: FragmentBeaconBinding? = null
    private val binding get() = _binding!!

    private lateinit var wifiManager: WifiManager

    private val apAdapter = AccessPointAdapter(AccessPointAdapter.Mode.NAVIGATE) { ap, _ ->
        openDetail(ap)
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) handleScan()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) scan()
        else Toast.makeText(requireContext(), "需要位置權限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBeaconBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        binding.rvBeaconAps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = apAdapter
        }
        binding.btnBeaconScan.setOnClickListener { requestAndScan() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            binding.tvBeaconNote.text = "⚠ IE 詳細資訊需要 Android 11+"
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(
            scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(scanReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestAndScan() {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) scan() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun scan() {
        binding.tvBeaconStatus.text = "掃描中…"
        binding.btnBeaconScan.isEnabled = false
        if (!wifiManager.startScan()) handleScan()
    }

    @Suppress("DEPRECATION")
    private fun handleScan() {
        binding.btnBeaconScan.isEnabled = true
        val results = wifiManager.scanResults ?: emptyList()
        apAdapter.updateList(results)
        results.forEach { BeaconDetailActivity.ssidCache[it.BSSID] = it.SSID }
        val mc = results.count { it.is80211mcResponder }
        binding.tvBeaconStatus.text = "${results.size} 個 AP  |  RTT(mc): $mc  — 點選查看 IE"
    }

    private fun openDetail(ap: ScanResult) {
        val intent = Intent(requireContext(), BeaconDetailActivity::class.java)
        intent.putExtra(BeaconDetailActivity.EXTRA_SCAN_RESULT, ap)
        startActivity(intent)
    }
}

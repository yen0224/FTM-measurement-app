package com.example.wififtm

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.wififtm.databinding.FragmentDeviceCapabilityBinding

class DeviceCapabilityFragment : Fragment() {

    private var _binding: FragmentDeviceCapabilityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceCapabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populate()
    }

    override fun onResume() {
        super.onResume()
        // RTT availability can change at runtime (e.g. location toggled off)
        _binding?.let { refreshRttReady(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun populate() {
        val b = binding
        val pm = requireContext().packageManager

        // ── Device Info ──────────────────────────────────────────────────────
        b.tvManufacturerValue.text = Build.MANUFACTURER
        b.tvModelValue.text = Build.MODEL
        b.tvApiValue.text = "${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})"

        // ── RTT Support Status ───────────────────────────────────────────────
        val hasRtt = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
        setStatus(b.tvMcCapIcon, b.tvMcCapStatus, hasRtt, "支援", "不支援")

        val azOs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasRtt
        setStatus(b.tvAzOsIcon, b.tvAzOsStatus, azOs,
            "支援 (API 33+)",
            if (!hasRtt) "硬體不支援" else "需要 Android 13+"
        )

        refreshRttReady(b)

        // ── Ranging Capabilities ─────────────────────────────────────────────
        // RangingCapabilities (API 33) is not in the public SDK stub.
        // Derive best-effort answers from PackageManager feature flags.

        // D2AP requires WiFi RTT hardware
        setStatus(b.tvD2apIcon, b.tvD2apStatus, hasRtt, "支援", "不支援")

        // D2D requires WiFi RTT + WiFi Aware (NAN)
        val hasAware = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        setStatus(b.tvD2dIcon, b.tvD2dStatus, hasRtt && hasAware, "支援", "不支援")

        // 802.11az NTB: OS 13+ + RTT hardware; per-device accuracy limited by public API
        setStatus(b.tvAzNtbIcon, b.tvAzNtbStatus, azOs, "可能支援", "不支援")

        // Distance limits are not exposed in the public SDK
        b.tvMaxDistValue.text = "—"
        b.tvMinDistValue.text = "—"
        b.tvCapNote.text = "測距距離限制未開放於公開 API"
        b.tvCapNote.visibility = View.VISIBLE
    }

    private fun refreshRttReady(b: FragmentDeviceCapabilityBinding) {
        val rttMgr = requireContext().getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
        val available = rttMgr?.isAvailable == true
        setStatus(b.tvRttReadyIcon, b.tvRttReadyStatus, available, "可用", "不可用")
    }

    private fun setStatus(iconView: TextView, statusView: TextView, ok: Boolean, positive: String, negative: String) {
        iconView.text = if (ok) "✓" else "✗"
        iconView.setTextColor(ContextCompat.getColor(requireContext(), if (ok) R.color.ios_green else R.color.ios_red))
        statusView.text = if (ok) positive else negative
    }
}

package com.example.wififtm

import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wififtm.databinding.ActivityBeaconDetailBinding

class BeaconDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN_RESULT = "scan_result"
        // Simple cache so BeaconActivity can share SSID strings
        val ssidCache = mutableMapOf<String, String>()
    }

    private lateinit var binding: ActivityBeaconDetailBinding
    private val ieAdapter = IeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeaconDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        @Suppress("DEPRECATION")
        val ap: ScanResult? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(EXTRA_SCAN_RESULT, ScanResult::class.java)
        else
            intent.getParcelableExtra(EXTRA_SCAN_RESULT)

        if (ap == null) { finish(); return }

        supportActionBar?.title = ap.SSID.ifBlank { ap.BSSID }

        binding.rvIeList.apply {
            layoutManager = LinearLayoutManager(this@BeaconDetailActivity)
            adapter = ieAdapter
        }

        bindApHeader(ap)
        loadIes(ap)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun bindApHeader(ap: ScanResult) {
        binding.tvDetailSsid.text = ap.SSID.ifBlank { "<Hidden SSID>" }
        binding.tvDetailBssid.text = ap.BSSID
        binding.tvDetailFreq.text = "${ap.frequency} MHz  |  CH ${freqToChannel(ap.frequency)}"
        binding.tvDetailRssi.text = "${ap.level} dBm"
        binding.tvDetailCaps.text = ap.capabilities
        binding.tvDetailRtt.text = if (ap.is80211mcResponder) "FTM Responder: ✓" else "FTM Responder: ✗"
        binding.tvDetailRtt.setTextColor(
            if (ap.is80211mcResponder) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )
        val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (ap.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                else -> "?"
            }
        } else "?"
        binding.tvDetailWidth.text = "Channel Width: $width"
    }

    private fun loadIes(ap: ScanResult) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            binding.tvIeNote.text = "IE 詳細資訊需要 Android 11+"
            return
        }
        val elements = ap.informationElements
        if (elements.isNullOrEmpty()) {
            binding.tvIeNote.text = "無 IE 資訊（部分裝置/驅動程式不提供）"
            return
        }
        val parsed = IeParser.parseAll(elements)
        binding.tvIeNote.text = "共 ${parsed.size} 個 Information Element"
        ieAdapter.setData(parsed)
    }

    private fun freqToChannel(freq: Int): Int {
        if (freq == 2484) return 14
        if (freq in 2412..2472) return (freq - 2412) / 5 + 1
        if (freq in 5170..5825) return (freq - 5000) / 5
        if (freq in 5955..7115) return (freq - 5955) / 5 + 1
        return -1
    }
}

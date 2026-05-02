package com.example.wififtm

import android.net.wifi.rtt.RangingResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wififtm.databinding.ItemFtmResultBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FtmResultItem(
    val result: RangingResult,
    val ssid: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class FtmResultAdapter : RecyclerView.Adapter<FtmResultAdapter.ViewHolder>() {

    private val items = mutableListOf<FtmResultItem>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    inner class ViewHolder(val binding: ItemFtmResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FtmResultItem) {
            val r = item.result
            val success = r.status == RangingResult.STATUS_SUCCESS

            binding.tvResultSsid.text = item.ssid.ifBlank { "<Hidden>" }
            binding.tvResultBssid.text = "MAC: ${r.macAddress}"
            binding.tvTimestamp.text = timeFormat.format(Date(item.timestampMs))

            if (success) {
                binding.tvResultStatus.text = "SUCCESS"
                binding.tvResultStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.success_green)
                )

                val distanceMm = r.distanceMm
                val distanceCm = distanceMm / 10.0
                val stdDevCm = r.distanceStdDevMm / 10.0

                binding.tvDistance.text = "%.1f".format(distanceCm)
                binding.tvDistanceStdDev.text = "±%.1f cm".format(stdDevCm)
                binding.tvDistanceMm.text = "RAW: ${distanceMm} mm ± ${r.distanceStdDevMm} mm"

                binding.tvResultRssi.text = r.rssi.toString()
                binding.tvRssiSignal.text = rssiToSignalLabel(r.rssi)
                binding.tvRssiSignal.setTextColor(rssiToColor(r.rssi, binding.root.context))

                val attempted = r.numAttemptedMeasurements
                val succeeded = r.numSuccessfulMeasurements
                val rate = if (attempted > 0) succeeded * 100 / attempted else 0
                binding.tvMeasurements.text = "嘗試: $attempted  成功: $succeeded  成功率: $rate%"

                // Raw data section
                val raw = buildString {
                    appendLine("timestamp_µs: ${r.rangingTimestampMillis * 1000}")
                    appendLine("distance_mm: $distanceMm")
                    appendLine("stddev_mm: ${r.distanceStdDevMm}")
                    appendLine("rssi_dbm: ${r.rssi}")
                    appendLine("num_attempted: ${r.numAttemptedMeasurements}")
                    appendLine("num_successful: ${r.numSuccessfulMeasurements}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        appendLine("is_80211mc: ${r.is80211mcMeasurement}")
                    }
                }
                binding.tvRawData.text = raw.trimEnd()

            } else {
                binding.tvResultStatus.text = "FAIL (${r.status})"
                binding.tvResultStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.fail_red)
                )
                binding.tvDistance.text = "N/A"
                binding.tvDistanceStdDev.text = ""
                binding.tvDistanceMm.text = ""
                binding.tvResultRssi.text = "N/A"
                binding.tvRssiSignal.text = ""
                binding.tvMeasurements.text = "狀態碼: ${r.status}"
                binding.tvRawData.text = "status: ${statusToString(r.status)}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFtmResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    fun addResult(item: FtmResultItem) {
        items.add(0, item)  // newest first
        notifyItemInserted(0)
    }

    fun addResults(newItems: List<FtmResultItem>) {
        for (item in newItems.reversed()) {
            items.add(0, item)
        }
        notifyItemRangeInserted(0, newItems.size)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun getCount() = items.size

    private fun rssiToSignalLabel(rssi: Int): String = when {
        rssi >= -50 -> "極強"
        rssi >= -60 -> "強"
        rssi >= -70 -> "中"
        rssi >= -80 -> "弱"
        else -> "極弱"
    }

    private fun rssiToColor(rssi: Int, context: android.content.Context): Int =
        ContextCompat.getColor(
            context, when {
                rssi >= -60 -> R.color.success_green
                rssi >= -70 -> android.R.color.holo_orange_light
                else -> R.color.fail_red
            }
        )

    private fun statusToString(status: Int): String = when (status) {
        RangingResult.STATUS_SUCCESS -> "SUCCESS"
        RangingResult.STATUS_FAIL -> "FAIL"
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC ->
            "RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC"
        else -> "UNKNOWN($status)"
    }
}

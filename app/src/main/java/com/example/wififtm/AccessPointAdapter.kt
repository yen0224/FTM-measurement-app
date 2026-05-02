package com.example.wififtm

import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wififtm.databinding.ItemAccessPointBinding

class AccessPointAdapter(
    private val mode: Mode,
    private val onItemClick: (ScanResult, Boolean) -> Unit
) : RecyclerView.Adapter<AccessPointAdapter.ViewHolder>() {

    enum class Mode { SELECT, NAVIGATE }

    private val items = mutableListOf<ScanResult>()
    private val selectedBssids = mutableSetOf<String>()

    inner class ViewHolder(val binding: ItemAccessPointBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ap: ScanResult, isLast: Boolean) {
            val isSelected = ap.BSSID in selectedBssids

            binding.tvSsid.text = ap.SSID.ifBlank { "<Hidden SSID>" }
            binding.tvBssid.text = ap.BSSID
            binding.tvRssi.text = ap.level.toString()
            binding.tvFrequency.text = "${ap.frequency} MHz"
            binding.tvChannel.text = "· CH ${frequencyToChannel(ap.frequency)}"

            // RSSI signal dot color
            val dotColor = when {
                ap.level >= -60 -> ContextCompat.getColor(binding.root.context, R.color.ios_green)
                ap.level >= -75 -> ContextCompat.getColor(binding.root.context, R.color.ios_orange)
                else -> ContextCompat.getColor(binding.root.context, R.color.ios_red)
            }
            binding.signalDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(dotColor)

            // 802.11mc badge
            val mcOk = ap.is80211mcResponder
            binding.tvRttSupport.text = if (mcOk) "mc ✓" else "mc ✗"
            binding.tvRttSupport.setTextColor(
                ContextCompat.getColor(binding.root.context,
                    if (mcOk) R.color.ios_green else R.color.ios_red)
            )

            // 802.11az AP-level support is not exposed in ScanResult public API
            binding.tvAzSupport.visibility = View.GONE

            // Mode-specific UI
            when (mode) {
                Mode.SELECT -> {
                    binding.cbSelected.visibility = View.VISIBLE
                    binding.tvChevron.visibility = View.GONE
                    binding.cbSelected.isChecked = isSelected
                    binding.rootLayout.setBackgroundColor(
                        if (isSelected) 0xFFE8F0FE.toInt() else android.graphics.Color.WHITE
                    )
                }
                Mode.NAVIGATE -> {
                    binding.cbSelected.visibility = View.GONE
                    binding.tvChevron.visibility = View.VISIBLE
                    binding.rootLayout.setBackgroundColor(android.graphics.Color.WHITE)
                }
            }

            // Hide last separator for cleaner card look
            binding.separator.visibility = if (isLast) View.GONE else View.VISIBLE

            binding.root.setOnClickListener {
                if (mode == Mode.SELECT) {
                    val nowSelected = ap.BSSID !in selectedBssids
                    if (nowSelected) selectedBssids.add(ap.BSSID) else selectedBssids.remove(ap.BSSID)
                    notifyItemChanged(bindingAdapterPosition)
                    onItemClick(ap, nowSelected)
                } else {
                    onItemClick(ap, false)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAccessPointBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position], position == items.size - 1)

    override fun getItemCount() = items.size

    fun updateList(newItems: List<ScanResult>) {
        items.clear()
        items.addAll(
            newItems.sortedWith(compareByDescending<ScanResult> { it.is80211mcResponder }
                .thenByDescending { it.level })
        )
        notifyDataSetChanged()
    }

    fun getSelectedAPs(): List<ScanResult> = items.filter { it.BSSID in selectedBssids }
    fun hasSelection() = selectedBssids.isNotEmpty()

    private fun frequencyToChannel(freq: Int): Int {
        if (freq == 2484) return 14
        if (freq in 2412..2472) return (freq - 2412) / 5 + 1
        if (freq in 5170..5825) return (freq - 5000) / 5
        if (freq in 5955..7115) return (freq - 5955) / 5 + 1
        return -1
    }
}

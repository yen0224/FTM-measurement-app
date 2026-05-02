package com.example.wififtm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wififtm.databinding.ItemIeBinding

class IeAdapter : RecyclerView.Adapter<IeAdapter.ViewHolder>() {

    private val items = mutableListOf<IeParser.ParsedIe>()

    inner class ViewHolder(val binding: ItemIeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ie: IeParser.ParsedIe) {
            val idLabel = if (ie.extId >= 0) "${ie.id}/ext${ie.extId}" else "${ie.id}"
            binding.tvIeId.text = idLabel
            binding.tvIeName.text = ie.name
            binding.tvIeLength.text = "${ie.lengthBytes}B"
            binding.tvIeSummary.text = ie.summary
            binding.tvIeHex.text = ie.hexDump

            // Color-code well-known IEs
            val idColor = when (ie.id) {
                0    -> 0xFF03DAC5.toInt()   // SSID – teal
                48   -> 0xFFFFC107.toInt()   // RSN – amber
                45, 191, 255 -> 0xFF6200EE.toInt()  // Caps – purple
                11   -> 0xFF4CAF50.toInt()   // BSS Load – green
                221  -> 0xFFFF5722.toInt()   // Vendor – orange
                else -> 0xFF9E9E9E.toInt()   // grey
            }
            binding.tvIeId.setTextColor(idColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemIeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    fun setData(list: List<IeParser.ParsedIe>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}

package com.example.wififtm

import android.net.wifi.ScanResult
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
object IeParser {

    data class ParsedIe(
        val id: Int,
        val extId: Int,       // -1 if not an extension IE
        val name: String,
        val lengthBytes: Int,
        val hexDump: String,
        val summary: String
    )

    fun parseAll(elements: List<ScanResult.InformationElement>): List<ParsedIe> =
        elements.map { parse(it) }

    fun parse(ie: ScanResult.InformationElement): ParsedIe {
        val buf = ie.bytes
        val bytes = ByteArray(buf.limit()) { buf.get(it) }
        val extId = if (ie.id == 255 && bytes.isNotEmpty()) bytes[0].toInt() and 0xFF else -1
        val name = ieName(ie.id, extId)
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        val summary = summarize(ie.id, extId, bytes)
        return ParsedIe(ie.id, extId, name, bytes.size, hex, summary)
    }

    private fun ieName(id: Int, extId: Int): String {
        if (id == 255) return EXT_IE_NAMES[extId] ?: "Extension (ext=$extId)"
        return IE_NAMES[id] ?: "Unknown (id=$id)"
    }

    private fun summarize(id: Int, extId: Int, b: ByteArray): String = when (id) {
        0    -> if (b.isEmpty()) "<Hidden SSID>" else String(b, Charsets.UTF_8)
        1, 50 -> parseRates(b)
        3    -> if (b.isNotEmpty()) "Channel ${u8(b, 0)}" else "?"
        7    -> if (b.size >= 3) "Country: ${String(b.copyOf(2))}, Env: ${b[2].toInt().toChar()}" else "?"
        11   -> parseBssLoad(b)
        32   -> if (b.isNotEmpty()) "Max TX Power Reduction: ${u8(b, 0)} dB" else "?"
        45   -> parseHtCaps(b)
        48   -> parseRsn(b)
        54   -> if (b.size >= 2) "MDID: 0x%02X%02X, FT Cap: 0x%02X".format(u8(b,1), u8(b,0), if (b.size > 2) u8(b,2) else 0) else "?"
        61   -> parseHtOp(b)
        127  -> parseExtCaps(b)
        191  -> parseVhtCaps(b)
        192  -> parseVhtOp(b)
        221  -> parseVendor(b)
        255  -> when (extId) {
            35   -> parseHeCaps(b)
            36   -> parseHeOp(b)
            106  -> "FTM Sync Info present"
            108  -> "EHT Capabilities (802.11be)"
            109  -> "EHT Operation"
            else -> ""
        }
        else -> ""
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16le(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)

    private fun parseRates(b: ByteArray): String {
        val rates = b.map { byte ->
            val r = (byte.toInt() and 0x7F) * 0.5
            val basic = if (byte.toInt() and 0x80 != 0) "*" else ""
            "${if (r == r.toLong().toDouble()) r.toLong() else r}$basic"
        }
        return rates.joinToString(", ") + " Mbps"
    }

    private fun parseBssLoad(b: ByteArray): String {
        if (b.size < 5) return "?"
        val stations = u16le(b, 0)
        val util = u8(b, 2) * 100 / 255
        val avail = u16le(b, 3)
        return "Stations: $stations, Channel Util: $util%, Avail Admission: $avail"
    }

    private fun parseHtCaps(b: ByteArray): String {
        if (b.size < 2) return "?"
        val info = u16le(b, 0)
        val parts = buildList {
            if (info and 0x0001 != 0) add("LDPC")
            add(if (info and 0x0002 != 0) "40MHz" else "20MHz")
            if (info and 0x0020 != 0) add("SGI-20")
            if (info and 0x0040 != 0) add("SGI-40")
            if (info and 0x0080 != 0) add("TX-STBC")
            if (info and 0x0400 != 0) add("DSSS/CCK-40")
        }
        val streams = if (b.size >= 12) {
            val mcsSet = b.copyOfRange(3, minOf(13, b.size))
            "SS: ${countSpatialStreams(mcsSet)}"
        } else ""
        return (parts + listOfNotNull(streams.ifBlank { null })).joinToString(", ")
    }

    private fun countSpatialStreams(mcs: ByteArray): Int {
        var ss = 0
        for (i in 0..3) { if (i < mcs.size && mcs[i] != 0.toByte()) ss = i + 1 }
        return ss.coerceAtLeast(1)
    }

    private fun parseHtOp(b: ByteArray): String {
        if (b.isEmpty()) return "?"
        val primary = u8(b, 0)
        val secOffset = if (b.size > 1) when (u8(b, 1) and 0x03) {
            1 -> "+1 (Above)"
            3 -> "-1 (Below)"
            else -> "None"
        } else "?"
        return "Primary CH: $primary, Secondary: $secOffset"
    }

    private fun parseRsn(b: ByteArray): String {
        if (b.size < 4) return "?"
        val parts = mutableListOf<String>()
        // Version
        val version = u16le(b, 0)
        parts.add("v$version")
        if (b.size < 8) return parts.joinToString(", ")
        // Group cipher
        parts.add("Group: ${cipherName(u8(b, 7))}")
        if (b.size < 10) return parts.joinToString(", ")
        // Pairwise ciphers
        val pwCount = u16le(b, 8)
        var off = 10
        val pw = mutableListOf<String>()
        repeat(pwCount.coerceAtMost(8)) {
            if (off + 4 <= b.size) { pw.add(cipherName(u8(b, off + 3))); off += 4 }
        }
        if (pw.isNotEmpty()) parts.add("Pairwise: ${pw.joinToString("+")}")
        if (off + 2 > b.size) return parts.joinToString(", ")
        // AKM
        val akmCount = u16le(b, off); off += 2
        val akm = mutableListOf<String>()
        repeat(akmCount.coerceAtMost(8)) {
            if (off + 4 <= b.size) { akm.add(akmName(u8(b, off + 3))); off += 4 }
        }
        if (akm.isNotEmpty()) parts.add("AKM: ${akm.joinToString("+")}")
        // RSN Capabilities
        if (off + 2 <= b.size) {
            val cap = u16le(b, off)
            if (cap and 0x0040 != 0) parts.add("MFP-capable")
            if (cap and 0x0080 != 0) parts.add("MFP-required")
        }
        return parts.joinToString(", ")
    }

    private fun cipherName(s: Int) = when (s) {
        0 -> "None"; 1 -> "WEP-40"; 2 -> "TKIP"; 4 -> "CCMP-128"
        5 -> "WEP-104"; 6 -> "BIP"; 8 -> "GCMP-128"; 9 -> "GCMP-256"
        10 -> "CCMP-256"; else -> "($s)"
    }

    private fun akmName(s: Int) = when (s) {
        1 -> "802.1X"; 2 -> "PSK"; 3 -> "FT/802.1X"; 4 -> "FT/PSK"
        5 -> "802.1X-SHA256"; 6 -> "PSK-SHA256"; 7 -> "TDLS"
        8 -> "SAE"; 9 -> "FT/SAE"; 12 -> "FT/802.1X-SHA384"
        18 -> "OWE"; 24 -> "SAE-EXT-KEY"; else -> "($s)"
    }

    private fun parseExtCaps(b: ByteArray): String {
        val caps = mutableListOf<String>()
        if (b.size > 2 && u8(b, 2) and 0x08 != 0) caps.add("BSS Transition")
        if (b.size > 3 && u8(b, 3) and 0x40 != 0) caps.add("TDLS")
        if (b.size > 4 && u8(b, 4) and 0x40 != 0) caps.add("WNM-Sleep")
        if (b.size > 6 && u8(b, 6) and 0x01 != 0) caps.add("FTM-Responder")
        if (b.size > 6 && u8(b, 6) and 0x02 != 0) caps.add("FTM-Initiator")
        if (b.size > 7 && u8(b, 7) and 0x20 != 0) caps.add("TWT-Responder")
        return if (caps.isEmpty()) "${b.size} bytes" else caps.joinToString(", ")
    }

    private fun parseVhtCaps(b: ByteArray): String {
        if (b.size < 4) return "?"
        val cap = b[0].toLong() and 0xFF or ((b[1].toLong() and 0xFF) shl 8) or
                  ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        val chWidth = when ((cap shr 2) and 0x3L) {
            0L -> "80MHz"; 1L -> "160MHz"; 2L -> "80+80MHz"; else -> "?"
        }
        val parts = mutableListOf(chWidth)
        if (cap and 0x10 != 0L) parts.add("SGI-80")
        if (cap and 0x20 != 0L) parts.add("SGI-160")
        if (cap and 0x40 != 0L) parts.add("TX-STBC")
        if (cap and 0x1000 != 0L) parts.add("SU-BF")
        if (cap and 0x80000 != 0L) parts.add("MU-BF")
        return parts.joinToString(", ")
    }

    private fun parseVhtOp(b: ByteArray): String {
        if (b.size < 3) return "?"
        val width = when (u8(b, 0)) {
            0 -> "20/40MHz"; 1 -> "80MHz"; 2 -> "160MHz"; 3 -> "80+80MHz"; else -> "?"
        }
        return "$width, Center0: ${u8(b, 1)}, Center1: ${u8(b, 2)}"
    }

    private fun parseVendor(b: ByteArray): String {
        if (b.size < 4) return "?"
        val oui = "%02X:%02X:%02X".format(u8(b,0), u8(b,1), u8(b,2))
        val type = u8(b, 3)
        val vendor = when {
            u8(b,0)==0x00 && u8(b,1)==0x50 && u8(b,2)==0xF2 -> when (type) {
                1 -> "MS-WPA"; 2 -> "MS-WMM"; 4 -> "MS-WPS"; else -> "Microsoft"
            }
            u8(b,0)==0x00 && u8(b,1)==0x0C && u8(b,2)==0xE7 -> "Cisco"
            u8(b,0)==0x00 && u8(b,1)==0x17 && u8(b,2)==0xF2 -> "Apple"
            u8(b,0)==0x8C && u8(b,1)==0xFD && u8(b,2)==0xF0 -> "Qualcomm"
            u8(b,0)==0x00 && u8(b,1)==0x03 && u8(b,2)==0x7F -> "Atheros"
            else -> ""
        }
        return "OUI: $oui${if (vendor.isNotEmpty()) " ($vendor)" else ""}, Type: $type"
    }

    private fun parseHeCaps(b: ByteArray): String {
        if (b.size < 2) return "HE Capabilities"
        // b[0] is ext ID (35), actual caps start at b[1]
        val macCap = if (b.size > 1) u8(b, 1) else 0
        val parts = mutableListOf<String>()
        if (macCap and 0x02 != 0) parts.add("TWT-Req")
        if (macCap and 0x04 != 0) parts.add("TWT-Resp")
        if (b.size > 7) {
            val phyCap = u8(b, 7)
            if (phyCap and 0x08 != 0) parts.add("40MHz-2.4G")
            if (phyCap and 0x04 != 0) parts.add("40/80MHz-5G")
            if (phyCap and 0x10 != 0) parts.add("160MHz")
            if (phyCap and 0x20 != 0) parts.add("80+80MHz")
        }
        return if (parts.isEmpty()) "HE (802.11ax)" else "HE: ${parts.joinToString(", ")}"
    }

    private fun parseHeOp(b: ByteArray): String {
        if (b.size < 2) return "HE Operation"
        // b[0] is ext ID (36), HE Op params start at b[1..3]
        val bssColor = if (b.size > 4) u8(b, 4) and 0x3F else 0
        return "HE Operation, BSS Color: $bssColor"
    }

    private val IE_NAMES = mapOf(
        0 to "SSID", 1 to "Supported Rates", 3 to "DS Parameter Set",
        5 to "TIM", 6 to "IBSS Parameter Set", 7 to "Country",
        10 to "Request", 11 to "BSS Load", 32 to "Power Constraint",
        33 to "Power Capability", 35 to "TPC Report", 36 to "Supported Channels",
        37 to "Channel Switch Announcement", 42 to "ERP Information",
        45 to "HT Capabilities", 46 to "QoS Capability",
        48 to "RSN Information", 50 to "Extended Supported Rates",
        54 to "Mobility Domain (FT)", 55 to "Fast BSS Transition",
        61 to "HT Operation", 62 to "Secondary Channel Offset",
        74 to "Overlapping BSS Scan", 107 to "Interworking",
        108 to "Advertisement Protocol", 111 to "Roaming Consortium",
        127 to "Extended Capabilities", 191 to "VHT Capabilities",
        192 to "VHT Operation", 195 to "TX Power Envelope",
        199 to "Operating Mode Notification",
        221 to "Vendor Specific", 255 to "Extension Element"
    )

    private val EXT_IE_NAMES = mapOf(
        35 to "HE Capabilities (802.11ax)", 36 to "HE Operation",
        37 to "UORA Parameter Set", 38 to "BSS Color Change",
        40 to "MU EDCA Parameter Set", 43 to "Spatial Reuse",
        106 to "FTM Synchronization Info",
        108 to "EHT Capabilities (802.11be)", 109 to "EHT Operation",
        110 to "Multi-Link Element"
    )
}

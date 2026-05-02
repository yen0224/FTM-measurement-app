# WiFi FTM Ranger

An Android app for sending IEEE 802.11mc/az Fine Timing Measurement (FTM) requests and displaying ranging results in real time.

## Requirements

| Item | Requirement |
|------|-------------|
| Android version | 9.0+ (API 28+) |
| WiFi RTT hardware | Required for ranging (`android.hardware.wifi.rtt`) |
| Permissions | `ACCESS_FINE_LOCATION` · `NEARBY_WIFI_DEVICES` (Android 13+) |
| IE viewer | Android 11+ (API 30) for raw beacon Information Elements |

## Features

### FTM Ranging Tab

- **Device support panel** — shows hardware support status for 802.11mc, 802.11az (OS-level), and RTT runtime availability
- **AP scanner** — lists all visible access points, sorted by RTT support then RSSI; each AP displays mc support badge
- **Multi-AP ranging** — select one or more APs and start periodic ranging
- **Configurable interval** — set the ranging period in milliseconds (default 1000 ms)
- **Result cards** — each result shows:
  - Distance (cm) with standard deviation
  - RSSI (dBm) with signal quality label
  - Raw distance in mm ± stddev
  - Attempted / successful measurement counts
  - Full timestamp
  - `is80211mcMeasurement` flag (Android 14+)

### Beacon Viewer Tab

- Scans and lists all APs with RTT responder count
- Tap any AP to view its **raw Beacon Information Elements**:
  - IE ID, name, byte length, hex dump
  - Parsed summaries for common IEs (SSID, Supported Rates, DS Parameter Set, Country, BSS Load, HT/VHT/HE Capabilities, RSN, HT Operation, Extended Capabilities, Vendor Specific, FTM Sync Info, etc.)

## Parsed Information Elements

| ID | Name | Parsed fields |
|----|------|---------------|
| 0 | SSID | UTF-8 string |
| 1 / 50 | Supported / Extended Rates | Rate list with basic-rate markers |
| 3 | DS Parameter Set | Channel number |
| 7 | Country | Country code + environment |
| 11 | BSS Load | Station count, channel utilisation %, available admission |
| 45 | HT Capabilities | Channel width, LDPC, SGI-20/40, STBC, spatial streams |
| 48 | RSN | Version, group/pairwise ciphers, AKM suites, MFP flags |
| 54 | Mobility Domain | MDID, FT capability |
| 61 | HT Operation | Primary channel, secondary offset |
| 127 | Extended Capabilities | BSS Transition, TDLS, FTM Responder/Initiator, TWT |
| 191 | VHT Capabilities | Max channel width, SGI, STBC, beamforming |
| 192 | VHT Operation | Channel width, center frequencies |
| 221 | Vendor Specific | OUI + vendor identification (Microsoft, Cisco, Apple, Qualcomm, Atheros) |
| 255/35 | HE Capabilities | TWT, 40/80/160 MHz support |
| 255/36 | HE Operation | BSS color |
| 255/106 | FTM Synchronization Info | Presence flag |
| 255/108-109 | EHT (802.11be) | Capabilities / Operation |

## Building

1. Open the project root in **Android Studio**
2. Sync Gradle (`File → Sync Project with Gradle Files`)
3. Connect a device or start an emulator
4. Run (`Shift+F10`)

```
minSdk  28   (Android 9)
targetSdk 34  (Android 14)
compileSdk 34
```

## Notes

- **802.11az AP detection** — `ScanResult` does not expose a per-AP 802.11az flag in the public SDK. The az badge in the device support panel reflects OS-level support (API 33+), not per-AP capability.
- **Scan throttling** — Android throttles `WifiManager.startScan()` for background apps. If the scan is throttled, the last cached results are used automatically.
- **RTT availability** — `WifiRttManager.isAvailable()` can change at runtime (e.g. when the user disables Location). The panel refreshes on every `onResume`.

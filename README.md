# NetAnalyzer

![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![API](https://img.shields.io/badge/API-26%2B-brightgreen?)
![Android Studio](https://img.shields.io/badge/Android%20Studio-1976D2?logo=androidstudio&logoColor=fff)
![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)

[🇬🇧 English](README.md) · [🇪🇸 Español](README.es.md)

**NetAnalyzer** is a native Android application for auditing local networks without requiring superuser (root) privileges. It can discover active devices, identify their main characteristics (IP, MAC, manufacturer, estimated operating system, hostname), and detect exposed services through port scanning, all from a mobile device.

## What problem does it solve?

Traditional network auditing tools (Nmap, Wireshark, Nessus) require a desktop computer and, in many cases, elevated privileges. On Android, existing applications often have significant limitations: some require root, others offer reduced functionality, or they depend on external services.

NetAnalyzer fills that gap by providing a **portable, lightweight, and root-free** tool capable of performing preliminary security assessments directly from a smartphone. It is aimed at system administrators, support technicians, network students, and advanced users who need to inspect their home or corporate network.

## How does it work?

Its operation is based on a flow of discovery, analysis, and enrichment:

1. **Device discovery.** Several active and passive mechanisms run simultaneously:
   - **ARP**: reading the system ARP table (only on versions where permitted).
   - **ICMP**: native ping to detect reachable hosts.
   - **TCP**: connections to common ports to infer activity.
   - **mDNS/DNS-SD**: multicast queries to resolve `.local` names and advertised services.
   - **SSDP (UPnP)**: sending `M-SEARCH` to obtain XML descriptions from IoT devices.
   - **NetBIOS**: NBSTAT queries for Windows environments.

2. **Information fusion and correlation.** Results from all methods are consolidated into a thread-safe collection (`ListDeviceInfo`) that uses the IP address as the primary key. Each field (operating system, hostname, model, manufacturer) is wrapped in a `PriorityValue` with a confidence level, so the most reliable information prevails over less reliable data, regardless of the order in which results arrive.

3. **Fingerprinting.** Additional information is inferred from:
   - The **TTL** of ICMP/TCP responses (operating system estimate).
   - The **OUI prefixes** of MAC addresses (manufacturer).
   - Service **banners** (SSH, HTTP, etc.).

4. **AI enrichment (optional).** During SSDP discovery, headers and XML descriptions are sent to a language model hosted on OpenRouter.ai, which returns a structured JSON object with manufacturer, model, operating system, serial number, and services. This module is configured with the user's own API key.

5. **Port scanning.** Once active hosts are identified, a concurrent TCP scan is launched using Java NIO over a configurable set of ports (top 100, 500, or 1000).

6. **Reactive presentation.** The entire scan state (active phase, progress, discovered devices, network information) is centralized in a `ScanRepository` and propagated to the UI through `ViewModel` and `LiveData`.

## Main features

- **Multiple device detection**: ARP, ICMP, TCP, mDNS, SSDP, and NetBIOS, executed in parallel.
- **Smart data fusion**: avoids duplicates and resolves contradictions through priorities.
- **Fingerprinting**: operating system estimation via TTL, manufacturer via MAC, and banner grabbing.
- **AI enrichment**: semantic parsing of UPnP/SSDP devices via OpenRouter (optional).
- **Concurrent port scanning** with NIO and cooperative cancellation.
- **History**: up to 20 previous scans stored locally.
- **Advanced configuration**: method selection, port level, and specific parameters (pings, timeouts, threads, etc.).
- **Modern interface**: light/dark themes, three languages (Spanish, Basque, English), and animations.
- **Foreground service**: scanning continues in the background with a notification and cancellation option.

## Architecture and technologies

The application follows a modular architecture inspired by **Clean Architecture** and the **MVVM** pattern:

- **Presentation layer**: `Single-Activity` with `Fragments`, `RecyclerView`, and Material Design.
- **State layer**: `ScanViewModel` + `LiveData` + `ScanRepository` as the single source of truth.
- **Service layer**: `ScanService` (ForegroundService) orchestrates scanning and cancellation.
- **Network core**: `NetworkScanner` coordinates `DiscoveryMethod` implementations and port scanning.
- **Data models**: `NetworkInfo`, `DeviceInfo`, `ScanState`.

**Main technologies**:
- Java + Android SDK (API 26+)
- Java NIO (`SocketChannel` + `Selector`)
- `NsdManager` and `MulticastSocket` for mDNS/SSDP
- `OkHttp3` for HTTP calls (macvendors.com and OpenRouter)
- `Gson` and `SharedPreferences` for lightweight persistence

## Requirements and permissions

- **Android 8.0 (API 26)** or higher.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` (to obtain SSID/BSSID).
- Android 13+: `NEARBY_WIFI_DEVICES` and `POST_NOTIFICATIONS`.
- **Does not require root** on any version.

## Usage

1. Open the application: the current network information is displayed (IP, mask, gateway, DNS).
2. Press **SCAN** to start the analysis. The interface shows real-time progress and detected devices as they appear.
3. Go to the **Devices** tab to view the full list and see details for each device (open ports, services, metadata).
4. In **Settings**, you can change the theme, language, select discovery methods, configure the port level, and set your OpenRouter API key.

## Important limitations

- **ARP table access**: blocked from Android 10 onward without root. The ARP method only works on earlier versions or rooted devices; on the rest, discovery depends on ICMP, TCP, and multicast probes.
- **Passive traffic capture**: not possible without root. Only traffic generated by the device itself can be inspected through `VpnService`.
- **UDP scanning**: limited by the connectionless nature of the protocol and the lack of control over ICMP error messages; in practice, it is restricted to well-known ports (DNS, NTP, SNMP, SSDP).
- **Real ICMP ping and SYN scanning**: require raw sockets and therefore root.
- **MAC randomization**: on Android 10+, the MAC may not correspond to the actual hardware manufacturer, affecting OUI-based identification.

These limitations are a consequence of Android's security model and condition the scope of the analysis, but the combination of multiple mechanisms makes it possible to obtain a useful and reliable view of the network in most scenarios.

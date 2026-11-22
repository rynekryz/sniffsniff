<div align="center">
<img src="icon.png" width="160" height="160" style="display: block; margin: 0 auto"/>
<h1>SniffSniff</h1>
<p>An open-source network monitor for Android.</p>
<p>Watch live traffic, inspect packets, and see per-app usage, all locally with no cloud or external APIs.</p>
</div>
<p align="center">
  <a href="https://github.com/rynekryz/sniffsniff/stargazers"><img src="assets/badges/stars.svg" alt="GitHub stars" height="30"/></a>
  <a href="https://github.com/rynekryz/sniffsniff/tags"><img src="assets/badges/release.svg" alt="Latest release" height="30"/></a>
  <a href="LICENSE"><img src="assets/badges/licencegplv3.svg" alt="Licence GPLv3"/></a>
  <img src="assets/badges/android.svg" alt="Android"/>
  <img src="assets/badges/kotlin.svg" alt="Kotlin"/>
</p>

<p align="center">
  <a href="https://github.com/rynekryz/sniffsniff/releases"><img src="assets/badges/getgithub.svg" alt="Get it on GitHub" height="56"/></a>
</p>

---

## Screenshots

<div align="center">
<img src="/Screenshots/001535.png" width="24%" />
<img src="/Screenshots/001546.png" width="24%" />
<img src="/Screenshots/001551.png" width="24%" />
<img src="/Screenshots/001557.png" width="24%" />
</div>

---

## Features

- **Dashboard:** live up/down speed, a 60 second traffic graph, totals, and packet and connection counters
- **Connections:** IPs, ports, protocols and the owning app, with host names from TLS SNI, HTTP Host and DNS
- **Packet Inspector:** parsed IPv4, IPv6, TCP, UDP, ICMP, DNS, TLS and HTTP, plus a payload preview and hex viewer
- **Applications:** per-app traffic for the session, today, 7 days and 30 days
- **PCAP export** you can open in Wireshark
- **Material 3 Expressive** with spring transitions and expressive loading
- Dynamic color from your wallpaper, plus a monochrome fallback
- Light, dark, and pure black OLED
- GPL-3.0-or-later, with no analytics, telemetry, ads or accounts
- No cloud, no external APIs, everything happens on your device
- You choose which apps are captured, how much payload is kept, and how long history is stored
- etc.

---

## Technical Stack

- **UI:** Jetpack Compose with Material 3 Expressive
- **Architecture:** multi-module MVVM
- **DI:** Hilt
- **Database:** Room
- **Preferences:** DataStore
- **Capture:** Android VpnService with user-space TCP/UDP
- **Language:** Kotlin
- **Build:** Gradle (Kotlin DSL)

---

## Building

You'll need Android Studio with JDK 21. Copy `local.properties.template` to `local.properties`, set `sdk.dir`, then run:

```bash
./gradlew assembleDebug
```

## Limitations

- TLS and QUIC payloads are encrypted. SniffSniff shows metadata only and never decrypts anything.
- ICMP is captured and shown, but not forwarded. It's just visiting.
- Android's Private DNS hides DNS names, so turn it off if you want host names.
- Another active VPN or an always-on VPN lockdown will block capture.

---

## Project Structure

```
SniffSniff/
├── app/
├── core/
│   ├── common/
│   ├── database/
│   └── designsystem/
├── feature/
│   ├── dashboard/
│   ├── connections/
│   ├── inspector/
│   ├── applications/
│   └── settings/
└── network/
    ├── vpn/
    ├── capture/
    ├── forwarding/
    ├── parser/
    ├── flow/
    └── pcap/
```

---

## License

Licensed under GPL-3.0-or-later. See [LICENSE](LICENSE).

<div align="center">
<strong>Made with ♥︎ by Ryne</strong>
</div>
<div align="center">
  <a href="https://discord.gg/YKhWXXhKmC"><img src="assets/badges/discord.svg" alt="Discord"/></a>
  <a href="https://tiktok.com/@rynekryz"><img src="assets/badges/tiktok.svg" alt="TikTok"/></a>
</div>

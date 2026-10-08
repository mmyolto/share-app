# OJ-to-Dada

Native Android file-sharing app (Kotlin + Jetpack Compose) in the spirit of Quick Share.
Built for Meltronics Info-Tech, Port Moresby. **Works 100% offline** — no internet, no SIM data, no router required.

## What it does

| Feature | How |
|---|---|
| Android ⇄ Android, fast | Wi-Fi / LAN, or the app's own **offline hotspot** (Android Local-Only Hotspot) |
| Android ⇄ Android, no Wi-Fi at all | **Bluetooth** (RFCOMM) — slower, works anywhere |
| Android ⇄ Windows / Mac / Linux / iPhone / iPad / Chromebook | **No app needed on the other side.** The phone runs a tiny web server; the other device opens the page in any browser (still offline, over the same Wi-Fi or the phone's hotspot) to download and upload |
| QR codes | Receiver shows a QR; sender scans it. The QR carries the phone's address **and** the hotspot name/password, so the sender joins the hotspot automatically. A standard Wi-Fi QR is shown for iPhones / laptops |
| Send & receive at the same time | Always-listening server; every transfer runs on its own coroutine |
| Many devices at once | Tick several devices — all are sent to in parallel. Many senders can hit one receiver |
| No file-size limit | 64-bit sizes, streamed straight to storage (only limited by free space) |
| Any file type | .apk, .exe, .msi, .zip, videos, documents… plus **share installed apps as .apk** and **share OJ-to-Dada itself** |
| Animated instructions | 6-page animated guide on first launch, re-open any time from **How to use** |
| Background | Foreground service + Wi-Fi/wake locks keep big transfers going with the screen off |

Received files go to **Downloads/OJ-to-Dada**. Tap an .apk on the Home screen to install it.

## Build the APK

### Option A — Android Studio (recommended)
1. Install Android Studio (Ladybug or newer).
2. **File → Open** and choose this `OJ-to-Dada` folder. Let Gradle sync (needs internet once, to download Gradle/libraries — the *app* itself never uses the internet).
3. Plug in your phone (USB debugging on) and press **Run ▶**, or **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

### Option B — GitHub (no Android Studio needed)
1. Create a GitHub repo and push this folder.
2. The included workflow `.github/workflows/build-apk.yml` builds automatically.
3. Open the repo's **Actions** tab → latest run → download **OJ-to-Dada-apk**.

The release APK is signed with the debug key so it installs straight away. Use your own keystore before publishing on Google Play.

Requirements: Android 8.0 (API 26) or newer.

## Using it
- **Receive:** Home → *Receive* → show the QR. No Wi-Fi? switch on *Offline hotspot*.
- **Send:** Home → *Send* → pick Files / Photos / Apps → *Scan the receiver's QR* (or tick a device found nearby) → *Send*.
- **PC / iPhone:** Home → *PC / iPhone* → join the same Wi-Fi (or scan the Wi-Fi QR for the phone's hotspot) → scan / type the address in the browser.
- **Visible to nearby devices** switch on Home keeps the phone ready to receive in the background.

## Project layout
```
app/src/main/java/com/meltronics/ojtodada/
  core/Engine.kt      transfer engine: TCP + Bluetooth servers, send/receive, accept prompts, history
  core/Wire.kt        wire protocol (handshake, offer/accept, streamed files, acks)
  core/Hotspot.kt     offline hotspot (create + auto-join from QR)
  core/Lan.kt         local IPs + automatic discovery (mDNS/NSD)
  core/Bt.kt          Bluetooth RFCOMM server, client and scan
  core/WebShare.kt    built-in HTTP server for browsers (Windows/Mac/iPhone…)
  core/WebPage.kt     the self-contained page browsers see
  core/FileStore.kt   saving to Downloads/OJ-to-Dada, opening / installing
  core/QrCodec.kt     QR payloads + QR image generation (ZXing, offline)
  service/TransferService.kt  foreground service, Wi-Fi + wake locks
  ui/                 Compose screens + animated guide
```

## Honest limits
- **iPhone / iPad / Mac / Windows** use the browser page — there is no native iOS or Windows app in this project (Apple does not allow installing apps outside the App Store; a native iOS version would be a separate Swift project).
- Bluetooth transfers are Android ⇄ Android only and much slower (~100–300 KB/s) than Wi-Fi/hotspot (often 10–40 MB/s).
- While a phone runs the offline hotspot, some models disconnect from their normal Wi-Fi. Turn off normal mobile hotspot/tethering before starting it.
- Joining another phone's hotspot on Android 10+ shows a one-time system "Connect to device" prompt — tap Connect.
- The browser page stays usable only while the *PC / iPhone* screen is open on the phone.

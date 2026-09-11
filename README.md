# Hammy

Personal-use Fire TV / Android TV sideload app. A remote-friendly WebView shell over the public [xHamster](https://xhamster.com/) website.

**Package:** `com.personal.hammy`  
**No scraping, no CDN bypass, no downloading, no unofficial APIs** — just the official site in a TV browser.

## Screens

1. **18+ confirm** — Continue / Exit  
2. **Orientation** — Straight / Gay / Trans (large DPAD cards)  
3. **Categories** — ~25–40 popular categories shuffled each open; select up to 10; Continue or Skip  
4. **Browse** — full-screen WebView + shortcut bar for Home, Change prefs, and selected categories  

Preferences (orientation + categories) are stored in `SharedPreferences`.

## WebView user agent

Desktop Chrome UA (Windows):

```
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```

Chosen so the site serves its desktop layout, which is easier to navigate on a TV-sized WebView than the mobile site.

## Build

Requires JDK 17+ and Android SDK (platform 34, build-tools 34).

```bash
export ANDROID_HOME=/workspace/android-sdk
cd /workspace/hammy
./gradlew assembleDebug
```

Debug APK:

```
/workspace/hammy/app/build/outputs/apk/debug/app-debug.apk
```

## Install on Fire TV / Android TV

1. Enable **ADB debugging** (and **Apps from Unknown Sources** / sideload) on the device.  
2. Connect over the network:

```bash
adb connect <fire-tv-ip>:5555
adb devices
```

3. Install (replace path if needed):

```bash
adb install -r /workspace/hammy/app/build/outputs/apk/debug/app-debug.apk
```

4. Launch **Hammy** from the Apps row / Leanback launcher.


## Remote focus (Fire TV D-pad)

Browse WebView injects strong `:focus` CSS plus a floating lime/cyan ring that tracks `document.activeElement` (helps on consent/terms overlays). Videos get `tabindex`, enlarged control focus styles, and Center/Enter play-pause with an on-screen hint. Native AgeGate / Orientation / Categories use thicker focus borders and scale-up.

## Hardware Back

- If the WebView has history → `goBack()`  
- Otherwise → finish browse activity  

## License / use

Personal sideload only. Adult content (18+). You are responsible for local laws and site ToS.

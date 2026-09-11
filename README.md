# Hammy

Personal-use Fire TV / Android TV sideload app. Native Leanback-style browse grid over public [xHamster](https://xhamster.com/) listing pages; playback uses a dedicated WebView for the official video page.

**Package:** `com.personal.hammy`  
**No scraping CDNs, no paywall bypass, no downloading, no unofficial APIs** — card metadata comes from the same public HTTPS listing HTML a browser would get (`window.initials`).

## Screens

1. **18+ confirm** — Continue / Exit  
2. **Orientation** — Straight / Gay / Trans (large DPAD cards)  
3. **Categories** — ~25–40 popular categories shuffled each open; select up to 10; Continue or Skip  
4. **Browse (native)** — focusable poster/thumbnail cards in a TV grid + shortcut bar (Home, Change prefs, **All selected** AND-mix, individual categories). After category setup, opens the combined search (intersection) immediately — not Home.  
5. **Player** — fullscreen WebView for that video’s official page URL only; lite assist finds the main `<video>`, soft-focuses site **I’m 18+** / **Skip Ads** CTAs once (OK activates via hardClick/native tap — no auto-tap), and offers a native leanback **Close** (Back/Menu also `finish()` to the grid). Focus-ring + Center play-pause. No age-cookie seeding, no aggressive chrome-hide.

Preferences (orientation + categories) are stored in `SharedPreferences`.

## Listing data

Browse loads official URLs such as:

- Combined AND search: `https://xhamster.com/search/<slug1>+<slug2>+…` (gay: `/gay/search/…`, trans: `/shemale/search/…`)
- Single category: `https://xhamster.com/categories/<slug>` (and gay/shemale prefixes)
- orientation home pages (when categories were skipped)

and extracts publicly present `{title, thumb, url}` card fields. When tag metadata is present, the mix view can post-filter client-side so every selected slug matches. Empty/error states show a native message with **Retry**.

## Player WebView user agent

Desktop Chrome UA (Windows):

```
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```

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

Browse uses native focusable cards (scale + lime/cyan border). Player WebView injects chrome-hiding CSS/JS (retries) so only the main video/player remains, scaled to the viewport (`object-fit: contain`), with autoplay + fullscreen attempts. Also injects strong `:focus` CSS plus a floating ring (consent/terms overlays). Videos get `tabindex`, enlarged control focus styles, and Center/Enter play-pause with an on-screen hint. Native AgeGate / Orientation / Categories use thicker focus borders and scale-up. PlayerActivity uses immersive `LAYOUT_FULLSCREEN` / hidden system UI.

## Hardware Back

- **Player:** Back exits to the browse grid  
- **Browse:** finish browse activity  

## License / use

Personal sideload only. Adult content (18+). You are responsible for local laws and site ToS.

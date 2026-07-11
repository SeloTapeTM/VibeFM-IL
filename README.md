# Israel Radio (Android)

A minimal Capacitor wrapper around a single-file HTML/JS radio player
(`www/index.html`), packaged as a landscape-locked Android app for tablets
and car head units. No ads, no analytics, no accounts.

The same `www/index.html` also runs as a plain web page (no Capacitor
required — the native-bridge calls are feature-detected and no-op in a
browser) and is auto-deployed to GitHub Pages by
`.github/workflows/deploy-pages.yml` on every push that touches `www/`.

## What's in here

- `www/index.html` — the whole UI/player. Vanilla JS, hls.js for HLS
  streams, a plain `<audio>` element for direct MP3/AAC streams. Ships
  with **no stations built in** — tap "+ Add manually" (or the "+" tile)
  to add your own (name, URL, HLS/MP3 auto-detected from the URL), stored
  in `localStorage` on the device. No rebuild needed to add/remove a
  station.
  - "🔍 Scan for stations" tests a built-in list of known Israeli stream
    URLs live in the browser (plays each one briefly, real verification,
    not a static list) and only offers to add the ones that actually
    respond right now. Toggle "Auto-add stations that work" to skip the
    per-station confirmation, or leave it off to listen to each one
    yourself before adding it. A few of the candidates are pattern-matched
    guesses off the same CDN as a confirmed neighbor (not independently
    verified) — harmless if wrong, they just show "No response".
  - Manually adding a station also tests the URL before saving (same
    live-check as the scanner) and offers "Save anyway" if the test fails
    but you want to keep the URL regardless.
  - In practice, expect most stream URLs to fail this test even when
    they're genuinely live: Israeli broadcasters commonly restrict direct
    stream access to their own site's Origin/Referer (anti-hotlinking),
    which blocks *any* third-party page — this app included. There's no
    getting around that without a server-side relay, which is out of scope
    for a single-file client app. The scanner exists to sort out which
    stream URLs happen to allow this regardless.
  - If a stream fails outright, the test also tries following an HTTP
    redirect to wherever the URL actually resolves to and retries there
    (up to 4 hops) — this only works when the server sends CORS headers on
    every hop of the redirect (verified against a local test server: it
    genuinely works when they're present). Note this is a narrow case: a
    plain `<audio>` element already follows ordinary redirects on its own,
    so this only helps when the origin server refuses the request outright
    (e.g. before deciding whether to redirect) but a `fetch()` probe gets
    further. A dead/deprecated domain that never redirects at all (no
    CORS, no 3xx, just unreachable) can't be resolved this way — that's
    just a stale URL that needs replacing, not something to auto-detect.
  - A handful of candidates come from a long-abandoned (~2014) community
    Israeli-radio playlist project (`kodi-il/radio-il` /
    `eliransapir/live-il` on GitHub) rather than official sources — mostly
    plain HTTP, unverified. Testing them from this HTTPS-served app can
    give a false "No response" if Chrome's mixed-content handling silently
    blocks the http:// request before it even reaches the server; opening
    `tools/station-scanner.html` via `file://` avoids that entirely, so
    it's the more reliable place to test this batch specifically.
- `www/hls.min.js` — vendored copy of hls.js 1.5.13 (no CDN dependency at
  runtime).
- `android/` — the Capacitor-generated Android project, plus:
  - `RadioPlaybackService.java` — foreground service holding a
    `MediaSessionCompat`, a persistent notification (play/pause/next/prev),
    and hardware media-button handling (Bluetooth AVRCP / steering wheel,
    wired buttons). It does **not** decode audio itself — it just keeps the
    process alive through Doze/App Standby and mirrors state to/from the
    WebView's `<audio>` element.
  - `RadioMediaPlugin.java` — a small custom Capacitor plugin
    (`RadioMedia`) bridging JS ↔ native: JS calls `updateState()` on
    play/pause/station-change, and listens for a `mediaButton` event for
    play/pause/next/previous/stop coming from the notification or hardware
    buttons. Also exposes `setKeepScreenOn()`.
  - Landscape lock via `android:screenOrientation="landscape"`.
  - A network security config allowing cleartext HTTP, since some radio
    streams (built-in or user-added) aren't HTTPS.
  - A simple generated placeholder launcher icon (radio/antenna glyph on
    the app's accent blue).
- `tools/station-scanner.html` — a standalone maintainer utility, **not**
  part of the app and not linked from it. Open it directly in a browser
  (double-click, or `file://` path) with a real internet connection: it
  fetches Israel-tagged stations from the community-run
  [Radio-Browser](https://www.radio-browser.info/) database (or accepts a
  pasted JSON list instead), tests each one live with several running
  concurrently, and exports the ones that actually work as JSON — paste
  that back to update `CANDIDATE_STATIONS` in `www/index.html` with
  verified URLs instead of guesses. See "In practice, expect most stream
  URLs to fail..." below for why a lot of technically-live stations still
  won't pass.

No CI, no test framework, no state-management library — just the Capacitor
CLI and Gradle, per the "one person can maintain this" brief.

## ⚠️ APK not included — network policy blocks the build here

This project was built in a sandboxed cloud environment whose network
policy blocks `dl.google.com` and `maven.google.com` (Google's Android SDK
and Maven repositories). Every Android build — even just resolving the
Android Gradle Plugin itself — needs those hosts, so `gradle assembleDebug`
fails here with e.g.:

```
Could not GET 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.13.0/gradle-8.13.0.pom'.
Received status code 403 from server: Forbidden
```

This is a deliberate egress restriction in this environment, not a bug in
the project. All the source is complete and ready to build — you just need
to run the build somewhere with normal internet access (your own machine,
a GitHub Actions runner, Android Studio, etc.). Steps below.

## Building the debug APK

Prerequisites:
- Node.js 18+
- JDK 21+ (Capacitor 8's Android module compiles against source/target 21)
- Android SDK (Android Studio is the easiest way to get this — it installs
  the SDK, platform, and build-tools for you). If you'd rather use the
  command-line tools only, install `cmdline-tools`, then:
  ```
  sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
  ```
- Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your SDK path, or create
  `android/local.properties` with `sdk.dir=/path/to/Android/Sdk`.

From the project root:

```bash
npm install
npx cap sync android
cd android
./gradlew assembleDebug
```

The debug APK will be at:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

It's signed with Gradle's built-in debug keystore, so it's installable
right away (sideloading only — not eligible for Play Store).

### Installing the debug APK

**Via adb (device connected over USB or Wi-Fi debugging):**
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

**Via copying to the device:**
1. Copy `app-debug.apk` to the device (USB transfer, cloud drive, email to
   yourself, etc).
2. On the device, open the file with a file manager and tap install.
3. If blocked, enable "Install unknown apps" for that source app in
   Android Settings → Apps → Special access.

## Building a signed release APK

1. Generate a keystore (once — keep this file and its passwords safe,
   you'll need the same one for every future update):
   ```bash
   keytool -genkeypair -v \
     -keystore israel-radio-release.jks \
     -alias israelradio \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Add signing config to `android/app/build.gradle` (inside the `android {
   }` block), or pass the same values via `-P` properties on the command
   line to avoid committing secrets:
   ```groovy
   signingConfigs {
       release {
           storeFile file(System.getenv("RELEASE_STORE_FILE") ?: "../../israel-radio-release.jks")
           storePassword System.getenv("RELEASE_STORE_PASSWORD")
           keyAlias System.getenv("RELEASE_KEY_ALIAS") ?: "israelradio"
           keyPassword System.getenv("RELEASE_KEY_PASSWORD")
       }
   }
   buildTypes {
       release {
           signingConfig signingConfigs.release
           minifyEnabled false
           proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
       }
   }
   ```

3. Build:
   ```bash
   export RELEASE_STORE_FILE=/absolute/path/to/israel-radio-release.jks
   export RELEASE_STORE_PASSWORD=your-store-password
   export RELEASE_KEY_ALIAS=israelradio
   export RELEASE_KEY_PASSWORD=your-key-password
   cd android
   ./gradlew assembleRelease
   ```
   Output:
   ```
   android/app/build/outputs/apk/release/app-release.apk
   ```

   If you'd rather sign after the fact instead of wiring a signing config
   into Gradle, build the unsigned release APK (`minifyEnabled false`, no
   `signingConfig` set) and sign it manually:
   ```bash
   $ANDROID_HOME/build-tools/36.0.0/zipalign -v 4 \
     app-release-unsigned.apk app-release-aligned.apk
   $ANDROID_HOME/build-tools/36.0.0/apksigner sign \
     --ks israel-radio-release.jks \
     --ks-key-alias israelradio \
     --out app-release.apk \
     app-release-aligned.apk
   ```

## Notes / things to be aware of

- **Autoplay:** Capacitor's WebView already disables the "user gesture
  required" restriction for media playback, so `audio.play()` from JS
  works without a prior tap.
- **Background playback:** starts once you first select a station — the
  foreground service comes up with a low-priority, non-alerting
  notification showing the station name and play/pause/next/previous.
  Swiping the app away still leaves the service running (`START_STICKY`);
  fully stopping playback (tapping the notification's implicit stop via
  pause + task removal, or a device reboot) is what tears it down.
- **Keep screen on:** the toggle in the panel is off by default and calls
  `RadioMedia.setKeepScreenOn()`, which just sets/clears
  `FLAG_KEEP_SCREEN_ON` on the activity window — it does not force it on.
- **Stations:** there are no built-in stations — add your own from the
  "+" tile or the "+ Add a station" link. They're stored in `localStorage`
  inside the WebView, so they persist across app restarts but are local to
  the device (not synced anywhere, and lost if app data is cleared).

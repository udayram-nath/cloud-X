# CloudX — Android Studio Setup Guide

## What you're building
- **cloudx-server-app** → Install on Samsung M31 (home device, runs games + streams)
- **cloudx-client-app** → Install on Samsung J7 or any phone (connects + plays remotely)
- **cloudx-signaling-server** → Node.js server (run on your laptop or deploy online)

---

## STEP 1 — Install Android Studio

1. Download Android Studio from https://developer.android.com/studio
2. Run the installer, keep all defaults
3. When it opens, go to **SDK Manager** (top right wrench icon)
4. Make sure these are installed:
   - Android SDK Platform **API 34**
   - Android SDK Build-Tools **34.0.0**
   - Android Emulator (optional)
   - Android SDK Platform-Tools

---

## STEP 2 — Enable USB Debugging on your phones

**On Samsung M31:**
1. Settings → About Phone → Software Information
2. Tap **Build Number** 7 times until "Developer mode enabled"
3. Settings → Developer Options → turn ON **USB Debugging**
4. Plug M31 into laptop via USB → tap **Allow** on the phone popup

**Do the same on Samsung J7.**

---

## STEP 3 — Open the Server App project

1. Open Android Studio
2. Click **Open** (not New Project)
3. Navigate to the `cloudx-server-app` folder → click OK
4. Wait for Gradle sync to finish (takes 2-5 minutes first time)
5. If you see "Gradle sync failed", click the blue **Sync Now** link that appears

**If you get a missing JDK error:**
- File → Project Structure → SDK Location
- Set JDK to the one bundled with Android Studio

---

## STEP 4 — Add the WebRTC dependency repository

Open `cloudx-server-app/settings.gradle` and make sure it contains:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }  // needed for some WebRTC builds
    }
}
```

---

## STEP 5 — Build and install Server App on M31

1. In Android Studio, make sure **M31 appears in the device dropdown** (top toolbar)
2. Click the green **Run ▶** button
3. The app installs and opens on your M31
4. You'll see the CloudX Server screen — enter a password and tap **Start Server**
5. Android will show a "will start capturing your screen" dialog → tap **Start Now**
6. The server is now running in the background

---

## STEP 6 — Run the signaling server on your laptop

Open a terminal/command prompt in the `cloudx-signaling-server` folder:

```bash
npm install
node server.js
```

You should see:
```
CloudX signaling server running on port 8080
```

**Find your laptop's IP address:**
- Windows: open cmd → type `ipconfig` → look for IPv4 Address (e.g. 192.168.1.5)
- Mac/Linux: open terminal → type `ifconfig` → look for inet address

---

## STEP 7 — Open and configure the Client App

1. In Android Studio: File → Open → select `cloudx-client-app` folder
2. Wait for Gradle sync
3. Open `LoginActivity.kt` and note the default URL field
4. Open the client app on your J7 (same steps as step 5, select J7 as device)

---

## STEP 8 — Test on same WiFi first

1. Make sure M31, J7, and laptop are all on the **same WiFi**
2. Open CloudX (client app) on J7
3. Enter URL: `ws://YOUR_LAPTOP_IP:8080` (e.g. `ws://192.168.1.5:8080`)
4. Enter the password you set on the Server App
5. Tap **Connect**
6. You should see your M31's game list appear
7. Tap a game → it launches on M31 and streams to J7

---

## STEP 9 — Internet access from college (Tailscale)

1. Install **Tailscale** from Play Store on **both M31 and J7**
2. Create a free account at https://tailscale.com
3. Sign in on both phones with the **same Tailscale account**
4. In the Tailscale app, note your **M31's Tailscale IP** (looks like 100.x.x.x)
5. Also install Tailscale on your laptop and sign in
6. Run the signaling server on your laptop (step 6)
7. On J7 from college, enter URL: `ws://100.x.x.x:8080` (M31's Tailscale IP)
   OR `ws://LAPTOP_TAILSCALE_IP:8080` if running server on laptop
8. Connect → game library appears → tap a game → stream starts from home

---

## STEP 10 — Deploying signaling server online (optional but recommended)

For more reliable internet access, deploy the signaling server to a free host:

**Railway.app (easiest):**
1. Go to https://railway.app → sign up free
2. New Project → Deploy from GitHub repo → push your signaling server code
3. It gives you a URL like `wss://cloudx-signaling.railway.app`
4. Use this URL in the client app instead of your laptop IP
5. Now the signaling server runs 24/7 even when your laptop is off

---

## Common errors and fixes

| Error | Fix |
|---|---|
| `Unresolved reference: SurfaceViewRenderer` | Make sure `stream-webrtc-android` dependency synced. Try File → Sync Project with Gradle Files |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old version of the app from the phone first |
| `Connection refused` on client | Make sure signaling server is running and both devices on same WiFi |
| `MediaProjection permission denied` | Tap "Start Now" on the system dialog when server app asks |
| `Gradle sync failed: could not download` | Check internet connection, try File → Invalidate Caches → Restart |
| `ClassNotFoundException: SurfaceViewRenderer` | Clean project: Build → Clean Project → Rebuild Project |
| Build error in WebRtcManager.kt | Paste the exact error here and I'll fix the API mismatch |

---

## Project file structure (for reference)

```
cloudx-server-app/
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      java/com/cloudx/server/
        MainActivity.kt
        StreamingService.kt
        WebRtcManager.kt
        SignalingClient.kt
        GameScanner.kt
        ServerPrefs.kt
      res/
        layout/activity_main.xml
        drawable/input_background.xml
        values/colors.xml
        values/themes.xml

cloudx-client-app/
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      java/com/cloudx/client/
        LoginActivity.kt
        GameLibraryActivity.kt
        StreamActivity.kt
        WebRtcClient.kt
        SignalingClient.kt
        TouchInputOverlay.kt
        ClientState.kt
      res/
        layout/activity_login.xml
        layout/activity_game_library.xml
        layout/activity_stream.xml
        layout/item_game.xml
        drawable/game_card_bg.xml
        drawable/icon_bg.xml
        drawable/input_bg.xml
        values/colors.xml
        values/themes.xml

cloudx-signaling-server/
  server.js
  test-client.js
  package.json
```

---

## When you hit build errors

Don't worry — first build will likely have some. Just:
1. Copy the **full red error text** from the Build output tab in Android Studio
2. Paste it to me
3. I'll tell you exactly what to change

The most likely errors will be in `WebRtcManager.kt` due to WebRTC library API
surface — that's expected and easy to fix once we see the real compiler output.

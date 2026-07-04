# ☁️ CloudX

**Turn any old Android phone into a personal cloud gaming server — stream games from one phone to another, over WiFi or the internet, no root required.**

CloudX lets you run mobile games on a spare/old phone (the **host**) and play them remotely on any other phone (the **client**), with live video streamed over WebRTC and touch input sent back in real time. Think Steam Link or Moonlight, but for your two old Android phones instead of a gaming PC.

---

## How it works

```
┌──────────────────┐        WebSocket (signaling only)        ┌──────────────────┐
│   Client App      │ <───────────────────────────────────────> │  Signaling Server  │
│  (phone you play   │                                            │   (Node.js, runs   │
│   on remotely)      │                                            │   on a laptop/VPS) │
└─────────┬─────────┘                                            └─────────┬─────────┘
          │                                                                 │
          │                    WebRTC (video + touch data)                  │
          │                 — direct P2P once connected —                   │
          └─────────────────────────────────────────────────────────────────┘
                                       │
                            ┌──────────┴──────────┐
                            │    Server App         │
                            │  (phone hosting the    │
                            │   games — screen gets  │
                            │   captured & streamed) │
                            └────────────────────────┘
```

1. The **Server App** runs on the host phone, captures its screen via `MediaProjection`, and registers itself with the **Signaling Server**.
2. The **Client App** connects to the same signaling server, sees the host in a list, and requests a connection (password-protected).
3. Once accepted, the two phones negotiate a direct **WebRTC** connection. Video streams from host → client; touch events stream from client → host over a WebRTC data channel.
4. Touches are replayed on the host device via Android's **Accessibility API** (`dispatchGesture`) — no root, no ADB tether required.
5. The signaling server's only job is introducing the two phones and relaying SDP/ICE messages — it never sees or touches the actual video stream.

---

## Project structure

| Folder                     | What it is                                                             |
|-----------------------------|-------------------------------------------------------------------------|
| `cloudx-server-app/`         | Android app for the **host** phone — screen capture, WebRTC streaming, touch injection, game launching |
| `cloudx-client-app/`         | Android app for the **client** phone — connects, browses the host's games, renders the stream, sends touch input |
| `cloudx-signaling-server/`   | Node.js WebSocket server that pairs the two apps and relays WebRTC signaling |
| `.github/workflows/`         | CI workflow that builds both debug APKs automatically |

---

## Features

- 🎮 **Remote play** — stream and control games running on another phone
- 🔑 **Password-protected pairing** — host sets a password, client must know it to connect
- 📱 **Auto-discovery** — client sees the host as soon as both are connected to the signaling server
- 🕹️ **Real touch input** — taps and drags/swipes are replayed on the host via `AccessibilityService`, so on-screen joysticks and buttons work
- 🔋 **Live host status** — battery %, game list, and online state surface to the client
- 🌐 **Direct P2P streaming** — video never passes through the signaling server; it goes phone-to-phone (or via STUN/TURN if needed)
- 🌙 **Runs in the background** — the host app keeps streaming via a foreground service, even with the screen off

---

## Getting started

### 1. Run the signaling server
```bash
cd cloudx-signaling-server
npm install
node server.js
```
By default it listens on port `8080`. Find the machine's local IP (`ipconfig` / `ifconfig`) — you'll need it in step 3.

### 2. Install the two apps
Open `cloudx-server-app` and `cloudx-client-app` as **separate** Android Studio projects (they are intentionally not a single multi-module project, so each can be installed independently). See `cloudx-client-app/SETUP_GUIDE.md` for a full walkthrough, or `BUILDING_APKS.md` if you'd rather build APKs via GitHub Actions.

### 3. Point both apps at your signaling server
- **Server App**: edit `SIGNALING_SERVER_URL` in `StreamingService.kt` to `ws://<your-laptop-ip>:8080`
- **Client App**: enter the same URL on the login screen (it's saved for next time)

### 4. Pair and play
1. Launch the Server App on the host phone → set a password → tap **Start Server** → grant screen-capture permission.
2. Launch the Client App on the other phone → enter the server URL + password → it auto-connects to the host.
3. Pick a game from the library — it launches on the host and streams to you instantly.

---

## Requirements

- Both phones: **Android 8.0 (API 26)** or newer
- Host phone: needs **Accessibility permission** enabled once (`Settings → Accessibility → CloudX → Enable`) for touch input to work
- Same WiFi network for local testing, or a reachable signaling server (e.g. via Tailscale or a small VPS) for remote play over the internet

---

## Known limitations

- Single active session at a time (no multi-client queueing yet)
- No TURN server configured by default — connections behind strict/symmetric NAT (common on campus WiFi or mobile data) may fail without one
- Video-only — no audio streaming yet
- Some competitive titles with anti-cheat (e.g. COD Mobile, PUBG Mobile, Free Fire) may detect and reject `AccessibilityService`-simulated touches — test your target game early
- No adaptive bitrate — quality is fixed regardless of network conditions

---

## Tech stack

- **Android apps**: Kotlin, `stream-webrtc-android` (GetStream's WebRTC fork), `Java-WebSocket`, coroutines
- **Signaling server**: Node.js, Express, `ws`
- **Touch injection**: Android `AccessibilityService` + `dispatchGesture`
- **Screen capture**: Android `MediaProjection`

---

Built as a personal project to turn a spare phone into a couch-gaming rig — happy to take PRs or issues if you extend it further.

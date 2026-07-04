/**
 * CloudX Signaling Server
 * -----------------------
 * Purpose: Helps a "Server App" (phone hosting games) and a "Client App"
 * (phone playing remotely) find each other and exchange WebRTC connection
 * info (SDP offers/answers, ICE candidates). It does NOT carry video/audio —
 * once WebRTC connects, the actual game stream goes directly between the
 * two phones (or via TURN relay if direct connection fails).
 *
 * Run:  node server.js
 * Test: node test-client.js   (simulates a server-app + client-app pairing)
 */

const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// In-memory registry of online "Server Apps" (host devices like M31/J7)
// key: deviceId -> { ws, deviceName, password, games, connectedAt }
const hosts = new Map();

// In-memory registry of active client sessions waiting to pair
// key: sessionId -> { hostId, clientWs, hostWs }
const sessions = new Map();

function send(ws, type, payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type, ...payload }));
  }
}

function broadcastHostList() {
  const list = Array.from(hosts.values()).map((h) => ({
    deviceId: h.deviceId,
    deviceName: h.deviceName,
    gameCount: h.games.length,
    battery: h.battery,
    online: true,
  }));
  for (const client of wss.clients) {
    if (client.role === "client_app") {
      send(client, "host_list", { hosts: list });
    }
  }
}

wss.on("connection", (ws) => {
  ws.id = uuidv4();
  ws.role = null;

  ws.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return send(ws, "error", { message: "Invalid JSON" });
    }

    switch (msg.type) {
      /* ---------- SERVER APP (host) registers itself ---------- */
      case "register_host": {
        ws.role = "server_app";
        ws.deviceId = msg.deviceId || uuidv4();
        hosts.set(ws.deviceId, {
          deviceId: ws.deviceId,
          ws,
          deviceName: msg.deviceName || "Unnamed Device",
          passwordHash: msg.passwordHash, // client never sees raw password
          games: msg.games || [],
          battery: msg.battery ?? null,
          connectedAt: Date.now(),
        });
        send(ws, "registered", { deviceId: ws.deviceId });
        broadcastHostList();
        console.log(`[+] Host registered: ${msg.deviceName} (${ws.deviceId})`);
        break;
      }

      /* ---------- SERVER APP updates its game list / stats ---------- */
      case "update_host": {
        const host = hosts.get(ws.deviceId);
        if (host) {
          if (msg.games) host.games = msg.games;
          if (msg.battery !== undefined) host.battery = msg.battery;
          broadcastHostList();
        }
        break;
      }

      /* ---------- CLIENT APP registers itself ---------- */
      case "register_client": {
        ws.role = "client_app";
        ws.id = msg.clientId || ws.id;
        send(ws, "registered", { clientId: ws.id });
        broadcastHostList();
        break;
      }

      /* ---------- CLIENT requests to connect to a host ---------- */
      case "connect_request": {
        const host = hosts.get(msg.deviceId);
        if (!host) {
          return send(ws, "connect_failed", { reason: "Host offline" });
        }
        const sessionId = uuidv4();
        sessions.set(sessionId, {
          hostId: msg.deviceId,
          clientWs: ws,
          hostWs: host.ws,
        });
        // Ask the host app to verify the password locally and accept
        send(host.ws, "incoming_connection", {
          sessionId,
          passwordAttemptHash: msg.passwordHash,
        });
        break;
      }

      /* ---------- HOST accepts/rejects the connection ---------- */
      case "connection_decision": {
        const session = sessions.get(msg.sessionId);
        if (!session) return;
        if (msg.accepted) {
          send(session.clientWs, "connection_accepted", {
            sessionId: msg.sessionId,
            games: hosts.get(session.hostId)?.games || [],
          });
        } else {
          send(session.clientWs, "connection_rejected", {
            reason: "Wrong password or denied",
          });
          sessions.delete(msg.sessionId);
        }
        break;
      }

      /* ---------- WebRTC SIGNALING RELAY ----------
         Once connection is accepted, both sides exchange SDP/ICE
         through this relay until the direct WebRTC link is up. */
      case "webrtc_offer":
      case "webrtc_answer":
      case "webrtc_ice_candidate": {
        const session = sessions.get(msg.sessionId);
        if (!session) return;
        const target =
          ws === session.clientWs ? session.hostWs : session.clientWs;
        send(target, msg.type, { sessionId: msg.sessionId, payload: msg.payload });
        break;
      }

      case "launch_game": {
        const session = sessions.get(msg.sessionId);
        if (!session) return;
        send(session.hostWs, "launch_game", {
          sessionId: msg.sessionId,
          gameId: msg.gameId,
        });
        break;
      }

      case "end_session": {
        const session = sessions.get(msg.sessionId);
        if (session) {
          send(session.hostWs, "session_ended", { sessionId: msg.sessionId });
          send(session.clientWs, "session_ended", { sessionId: msg.sessionId });
          sessions.delete(msg.sessionId);
        }
        break;
      }

      default:
        send(ws, "error", { message: `Unknown type: ${msg.type}` });
    }
  });

  ws.on("close", () => {
    if (ws.role === "server_app" && ws.deviceId) {
      hosts.delete(ws.deviceId);
      broadcastHostList();
      console.log(`[-] Host disconnected: ${ws.deviceId}`);
    }
    // Clean up any sessions involving this socket
    for (const [sid, session] of sessions) {
      if (session.hostWs === ws || session.clientWs === ws) {
        const other = session.hostWs === ws ? session.clientWs : session.hostWs;
        send(other, "session_ended", { sessionId: sid, reason: "peer disconnected" });
        sessions.delete(sid);
      }
    }
  });
});

app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    hostsOnline: hosts.size,
    activeSessions: sessions.size,
  });
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log(`CloudX signaling server running on port ${PORT}`);
});

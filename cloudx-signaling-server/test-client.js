/**
 * Simulates:
 *  - A "Server App" (e.g. your M31) registering with 3 fake games
 *  - A "Client App" connecting, listing hosts, connecting, exchanging
 *    a fake WebRTC offer/answer, launching a game, then ending session.
 *
 * This proves the signaling server logic works end-to-end.
 * Real WebRTC video negotiation will use the same message flow once
 * the actual Android apps generate real SDP offers/answers.
 */

const WebSocket = require("ws");
const crypto = require("crypto");

const URL = "ws://localhost:8080";

function hash(pw) {
  return crypto.createHash("sha256").update(pw).digest("hex");
}

function log(tag, msg) {
  console.log(`[${tag}] ${msg}`);
}

async function run() {
  const hostWs = new WebSocket(URL);
  const clientWs = new WebSocket(URL);

  let sessionId = null;

  hostWs.on("open", () => {
    log("HOST", "connected to signaling server");
    hostWs.send(
      JSON.stringify({
        type: "register_host",
        deviceId: "m31-home",
        deviceName: "Uday's M31",
        passwordHash: hash("cloudx123"),
        games: [
          { id: "cod", name: "Call of Duty Mobile" },
          { id: "freefire", name: "Free Fire" },
          { id: "pubg", name: "PUBG Mobile" },
        ],
        battery: 87,
      })
    );
  });

  hostWs.on("message", (raw) => {
    const msg = JSON.parse(raw);
    log("HOST", `received: ${msg.type}`);

    if (msg.type === "incoming_connection") {
      // Host verifies password locally, then accepts
      const correct = msg.passwordAttemptHash === hash("cloudx123");
      hostWs.send(
        JSON.stringify({
          type: "connection_decision",
          sessionId: msg.sessionId,
          accepted: correct,
        })
      );
      log("HOST", `connection ${correct ? "ACCEPTED" : "REJECTED"}`);
    }

    if (msg.type === "webrtc_offer") {
      log("HOST", `got SDP offer, sending back a fake answer`);
      hostWs.send(
        JSON.stringify({
          type: "webrtc_answer",
          sessionId: msg.sessionId,
          payload: { sdp: "fake-answer-sdp-from-host" },
        })
      );
    }

    if (msg.type === "launch_game") {
      log("HOST", `launching game: ${msg.gameId} (would call MediaProjection here)`);
    }
  });

  clientWs.on("open", () => {
    log("CLIENT", "connected to signaling server");
    clientWs.send(JSON.stringify({ type: "register_client", clientId: "phone-j7" }));
  });

  clientWs.on("message", (raw) => {
    const msg = JSON.parse(raw);
    log("CLIENT", `received: ${msg.type}`);

    if (msg.type === "host_list" && msg.hosts.length > 0 && !sessionId) {
      const target = msg.hosts[0];
      log("CLIENT", `found host "${target.deviceName}" with ${target.gameCount} games. Connecting...`);
      clientWs.send(
        JSON.stringify({
          type: "connect_request",
          deviceId: target.deviceId,
          passwordHash: hash("cloudx123"),
        })
      );
    }

    if (msg.type === "connection_accepted") {
      sessionId = msg.sessionId;
      log("CLIENT", `connected! games available: ${msg.games.map((g) => g.name).join(", ")}`);

      // Simulate sending a real WebRTC SDP offer
      clientWs.send(
        JSON.stringify({
          type: "webrtc_offer",
          sessionId,
          payload: { sdp: "fake-offer-sdp-from-client" },
        })
      );
    }

    if (msg.type === "webrtc_answer") {
      log("CLIENT", "got SDP answer — WebRTC handshake would complete here, video stream starts");
      clientWs.send(
        JSON.stringify({ type: "launch_game", sessionId, gameId: "cod" })
      );

      setTimeout(() => {
        log("CLIENT", "ending session");
        clientWs.send(JSON.stringify({ type: "end_session", sessionId }));
        setTimeout(() => process.exit(0), 500);
      }, 1000);
    }

    if (msg.type === "session_ended") {
      log("CLIENT", "session ended cleanly");
    }
  });
}

run();

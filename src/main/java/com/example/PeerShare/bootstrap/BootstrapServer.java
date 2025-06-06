package com.example.PeerShare.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Dynamic UDP Bootstrap Server:
 * - Listens on UDP port 4000.
 * - Peers REGISTER themselves (or send HEARTBEAT) by sending a small JSON message:
 *     { "type": "REGISTER", "peerId": "<base64ID>", "ip": "1.2.3.4", "port": 5001 }
 *   (they should send this on startup and periodically thereafter).
 * - To fetch the current peer list, a peer sends:
 *     { "type": "GET_PEERS" }
 *   and the server replies with a JSON array of active peers.
 * - The server keeps track of each peer’s lastSeen timestamp. A background task
 *   prunes any peer not heard from in the last STALE_THRESHOLD_MS milliseconds.
 */
@SpringBootApplication
public class BootstrapServer implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(BootstrapServer.class, args);
    }

    // --- Configuration constants ---
    private static final int BOOTSTRAP_PORT = 4000;
    private static final int MAX_PACKET_SIZE = 2048;

    // If a peer has not sent REGISTER/HEARTBEAT within this many milliseconds, drop it.
    private static final long STALE_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(60);

    // How often to run the pruning task (in seconds)
    private static final long PRUNE_INTERVAL_SEC = 30;

    // In-memory map: peerIdBase64 --> PeerEntry
    private final ConcurrentMap<String, PeerEntry> activePeers = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // Single-threaded scheduler for periodic pruning
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void run(String... args) throws Exception {
        // 1. Schedule periodic pruning of stale peers
        scheduler.scheduleAtFixedRate(this::pruneStalePeers, PRUNE_INTERVAL_SEC, PRUNE_INTERVAL_SEC, TimeUnit.SECONDS);

        System.out.println("BootstrapServer starting on UDP port " + BOOTSTRAP_PORT);
        try (DatagramSocket socket = new DatagramSocket(BOOTSTRAP_PORT)) {
            byte[] buf = new byte[MAX_PACKET_SIZE];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String receivedJson = new String(packet.getData(), 0, packet.getLength()).trim();
                InetAddress requesterAddr = packet.getAddress();
                int requesterPort = packet.getPort();

                // Try parsing the incoming message as JSON
                JsonNode root;
                try {
                    root = mapper.readTree(receivedJson);
                } catch (Exception parseEx) {
                    System.out.printf("[Bootstrap] Received non-JSON or malformed: %s%n", receivedJson);
                    continue;
                }

                JsonNode typeNode = root.get("type");
                if (typeNode == null || !typeNode.isTextual()) {
                    System.out.printf("[Bootstrap] Missing or invalid 'type' field: %s%n", receivedJson);
                    continue;
                }

                String type = typeNode.asText();
                switch (type) {

                    // ------------------------------------------------------------
                    // 1) REGISTER (or HEARTBEAT) from a peer
                    //    { "type": "REGISTER",
                    //      "peerId": "<base64ID>",
                    //      "ip": "1.2.3.4",
                    //      "port": 5001 }
                    // ------------------------------------------------------------
                    case "REGISTER":
                    case "HEARTBEAT": {
                        JsonNode peerIdNode = root.get("peerId");
                        JsonNode ipNode     = root.get("ip");
                        JsonNode portNode   = root.get("port");

                        if (peerIdNode == null || ipNode == null || portNode == null
                                || !peerIdNode.isTextual() || !ipNode.isTextual() || !portNode.isInt()) {
                            System.out.printf("[Bootstrap] REGISTER missing fields: %s%n", receivedJson);
                            break;
                        }

                        String peerIdB64 = peerIdNode.asText();
                        String ip        = ipNode.asText();
                        int port         = portNode.asInt();
                        long now         = Instant.now().toEpochMilli();

                        // Update or create the PeerEntry
                        activePeers.put(peerIdB64, new PeerEntry(peerIdB64, ip, port, now));

                        System.out.printf(
                                "[Bootstrap] %s from peerId=%s at %s:%d (total peers=%d)%n",
                                type, peerIdB64, ip, port, activePeers.size()
                        );
                        break;
                    }

                    // ------------------------------------------------------------
                    // 2) GET_PEERS request
                    //    { "type": "GET_PEERS" }
                    //    → reply with a JSON array of current active peers:
                    //      [ { "peerId": "...", "ip": "...", "port": 5001 }, ... ]
                    // ------------------------------------------------------------
                    case "GET_PEERS": {
                        // Build a list of peer‐info maps
                        List<Map<String, Object>> replyList = new ArrayList<>();
                        long now = Instant.now().toEpochMilli();
                        for (PeerEntry entry : activePeers.values()) {
                            Map<String, Object> m = Map.of(
                                    "peerId", entry.peerId,
                                    "ip",      entry.ip,
                                    "port",    entry.port
                            );
                            replyList.add(m);
                        }

                        byte[] replyBytes = mapper.writeValueAsBytes(replyList);
                        DatagramPacket replyPacket = new DatagramPacket(
                                replyBytes, replyBytes.length,
                                requesterAddr, requesterPort
                        );
                        socket.send(replyPacket);

                        System.out.printf(
                                "[Bootstrap] Sent %d peers to %s:%d%n",
                                replyList.size(), requesterAddr.getHostAddress(), requesterPort
                        );
                        break;
                    }

                    // ------------------------------------------------------------
                    // 3) Unknown or unsupported message type
                    // ------------------------------------------------------------
                    default:
                        System.out.printf("[Bootstrap] Unknown 'type': %s%n", type);
                        break;
                }
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * Removes any peer that has not sent a REGISTER or HEARTBEAT
     * within the last STALE_THRESHOLD_MS milliseconds.
     */
    private void pruneStalePeers() {
        long cutoff = Instant.now().toEpochMilli() - STALE_THRESHOLD_MS;
        int before = activePeers.size();

        activePeers.values().removeIf(entry -> entry.lastSeen < cutoff);

        int after = activePeers.size();
        if (before != after) {
            System.out.printf(
                    "[Bootstrap] Pruned stale peers: before=%d, after=%d%n",
                    before, after
            );
        }
    }

    /**
     * Internal class to track a peer’s IP, port, and last‐seen timestamp.
     */
    private static class PeerEntry {
        final String peerId;    // Base64‐encoded 160‐bit ID
        final String ip;        // e.g. "127.0.0.1"
        final int port;         // e.g. 5001
        final long lastSeen;    // epoch millis

        PeerEntry(String peerId, String ip, int port, long lastSeen) {
            this.peerId = peerId;
            this.ip     = ip;
            this.port   = port;
            this.lastSeen = lastSeen;
        }
    }
}

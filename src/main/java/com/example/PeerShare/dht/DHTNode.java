//
//package com.example.PeerShare.dht;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.example.PeerShare.util.PeerIdUtil;
//
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.net.InetAddress;
//import java.util.*;
//
///**
// * DHTNode (Phase 2):
// * 1. Generates a random 160-bit peerId.
// * 2. Binds UDP socket on given port.
// * 3. Bootstraps (HELLO → get JSON list → insert each into RoutingTable).
// * 4. startListener(): handle PingRequest, FindNodeRequest.
// *    - On Ping: update RT, send PingResponse.
// *    - On FindNode: update RT, compute up to K closest using RT.findClosest(...), send FindNodeResponse.
// * 5. interactiveMode(): commands:
// *    - ping <ip> <port>
// *    - findnode <targetPeerIdBase64>
// *    - peers
// *    - rt (print routing table summary)
// *    - exit
// * 6. iterativeLookup(targetPeerId):
// *    - α = 3 parallel queries.
// *    - At each round, pick α closest unqueried from known shortlist, send them FindNodeRequest.
// *    - Collect responses, merge lists, continue until no closer nodes appear.
// */
//public class DHTNode {
//    private static final String BOOTSTRAP_IP = "127.0.0.1";
//    private static final int BOOTSTRAP_PORT = 4000;
//    private static final int MAX_PACKET_SIZE = 4096;
//    private static final int ALPHA = 3;   // parallelism factor
//    private static final int K = KBucket.K; // bucket size = 20
//
//    private final byte[] myPeerId;        // raw 20 bytes
//    private final String myPeerIdB64;     // Base64
//    private final String myIp;
//    private final int myPort;
//
//    private final RoutingTable routingTable;
//    private DatagramSocket udpSocket;
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    public DHTNode(int port) throws Exception {
//        this.myPeerId = PeerIdUtil.generateRandomPeerId();
//        this.myPeerIdB64 = PeerIdUtil.encodeToBase64(myPeerId);
//        this.myIp = InetAddress.getLocalHost().getHostAddress();
//        this.myPort = port;
//        this.routingTable = new RoutingTable(myPeerId);
//        this.udpSocket = new DatagramSocket(myPort);
//
//        System.out.printf("[DHTNode %s] Listening on %s:%d%n", myPeerIdB64, myIp, myPort);
//    }
//
//    /**
//     * Bootstrap step: send "HELLO" to bootstrap, parse JSON list, insert each as NodeInfo into RT.
//     */
//    private void bootstrap() throws Exception {
//        String hello = "HELLO";
//        byte[] buf = hello.getBytes();
//        DatagramPacket pkt = new DatagramPacket(
//                buf, buf.length,
//                InetAddress.getByName(BOOTSTRAP_IP), BOOTSTRAP_PORT
//        );
//        udpSocket.send(pkt);
//        System.out.println("[DHTNode " + myPeerIdB64 + "] Sent HELLO to bootstrap.");
//
//        // Wait (blocking) for response
//        byte[] recvBuf = new byte[MAX_PACKET_SIZE];
//        DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
//        udpSocket.receive(recvPkt);
//        String jsonList = new String(recvPkt.getData(), 0, recvPkt.getLength());
//        System.out.println("[DHTNode " + myPeerIdB64 + "] Received peer list: " + jsonList);
//
//        // Parse JSON → List<Map<String,Object>>
//        List<Map<String, Object>> peers = mapper.readValue(
//                jsonList, new TypeReference<>() {}
//        );
//        // For each entry, convert to NodeInfo and insert into routingTable
//        for (Map<String, Object> entry : peers) {
//            String ip = (String) entry.get("ip");
//            int port = (Integer) entry.get("port");
//            String peerIdB64 = (String) entry.get("peerId");
//            byte[] peerId = PeerIdUtil.decodeFromBase64(peerIdB64);
//            NodeInfo ni = new NodeInfo(peerId, ip, port);
//            routingTable.update(ni);
//        }
//        System.out.println("[DHTNode " + myPeerIdB64 + "] RoutingTable after bootstrap:\n" +
//                routingTable);
//    }
//
//    /**
//     * Listener thread: handle PingRequest or FindNodeRequest.
//     */
//    private void startListener() {
//        Thread t = new Thread(() -> {
//            try {
//                byte[] buf = new byte[MAX_PACKET_SIZE];
//                while (true) {
//                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
//                    udpSocket.receive(pkt);
//                    String json = new String(pkt.getData(), 0, pkt.getLength());
//
//                    // 1) Try PingRequest
//                    try {
//                        PingRequest preq = mapper.readValue(json, PingRequest.class);
//                        // Update RT
//                        byte[] rid = PeerIdUtil.decodeFromBase64(preq.getPeerIdBase64());
//                        NodeInfo remote = new NodeInfo(rid, preq.getSenderIp(), preq.getSenderPort());
//                        routingTable.update(remote);
//
//                        System.out.printf("[DHTNode %s] Received PING from %s:%d (peerId=%s)%n",
//                                myPeerIdB64,
//                                preq.getSenderIp(), preq.getSenderPort(), preq.getPeerIdBase64());
//
//                        // Reply with PingResponse
//                        PingResponse presp = new PingResponse(
//                                preq.getPeerIdBase64(),
//                                myPeerIdB64,
//                                myIp, myPort
//                        );
//                        byte[] out = mapper.writeValueAsBytes(presp);
//                        DatagramPacket respPkt = new DatagramPacket(
//                                out, out.length,
//                                InetAddress.getByName(preq.getSenderIp()),
//                                preq.getSenderPort()
//                        );
//                        udpSocket.send(respPkt);
//                        continue;
//                    } catch (Exception ignored) {
//                        // not a PingRequest
//                    }
//
//                    // 2) Try FindNodeRequest
//                    try {
//                        FindNodeRequest fnreq = mapper.readValue(json, FindNodeRequest.class);
//                        // Update RT with info about the requester
//                        byte[] rid = PeerIdUtil.decodeFromBase64(fnreq.getRequesterPeerIdBase64());
//                        NodeInfo requesterInfo = new NodeInfo(rid, fnreq.getRequesterIp(), fnreq.getRequesterPort());
//                        routingTable.update(requesterInfo);
//
//                        System.out.printf("[DHTNode %s] Received FIND_NODE(target=%s) from %s:%d%n",
//                                myPeerIdB64,
//                                fnreq.getTargetPeerIdBase64(),
//                                fnreq.getRequesterIp(), fnreq.getRequesterPort()
//                        );
//
//                        // Find k closest known peers to targetPeerId
//                        byte[] targetBytes = PeerIdUtil.decodeFromBase64(fnreq.getTargetPeerIdBase64());
//                        List<NodeInfo> closest = routingTable.findClosest(targetBytes, K);
//
//                        // Convert to DTOs
//                        List<NodeInfoDTO> dtoList = new ArrayList<>();
//                        for (NodeInfo ni : closest) {
//                            dtoList.add(NodeInfoDTO.fromNodeInfo(ni));
//                        }
//
//                        // Build response
//                        FindNodeResponse fnresp = new FindNodeResponse(
//                                fnreq.getRequesterPeerIdBase64(),
//                                myPeerIdB64,
//                                dtoList
//                        );
//                        byte[] out = mapper.writeValueAsBytes(fnresp);
//                        DatagramPacket respPkt = new DatagramPacket(
//                                out, out.length,
//                                InetAddress.getByName(fnreq.getRequesterIp()),
//                                fnreq.getRequesterPort()
//                        );
//                        udpSocket.send(respPkt);
//                        continue;
//                    } catch (Exception ignored) {
//                        // not a FindNodeRequest
//                    }
//
//                    // else: ignore unknown JSON
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//        t.setDaemon(true);
//        t.start();
//    }
//
//    /**
//     * Sends a PingRequest to a specific peer, waits for PingResponse, updates RT.
//     */
//    private void sendPing(String targetIp, int targetPort) throws Exception {
//        PingRequest preq = new PingRequest(myPeerIdB64, myIp, myPort);
//        byte[] out = mapper.writeValueAsBytes(preq);
//        DatagramPacket pkt = new DatagramPacket(
//                out, out.length,
//                InetAddress.getByName(targetIp), targetPort
//        );
//        udpSocket.send(pkt);
//        System.out.printf("[DHTNode %s] Sent PING to %s:%d%n", myPeerIdB64, targetIp, targetPort);
//
//        // Wait for PONG
//        udpSocket.setSoTimeout(2000);
//        try {
//            byte[] buf = new byte[MAX_PACKET_SIZE];
//            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
//            udpSocket.receive(respPkt);
//            PingResponse presp = mapper.readValue(
//                    new String(respPkt.getData(), 0, respPkt.getLength()), PingResponse.class
//            );
//            System.out.printf("[DHTNode %s] Received PONG from %s:%d (peerId=%s)%n",
//                    myPeerIdB64,
//                    presp.getThisIp(), presp.getThisPort(),
//                    presp.getThisPeerIdBase64()
//            );
//            // Update RT with who replied
//            byte[] rid = PeerIdUtil.decodeFromBase64(presp.getThisPeerIdBase64());
//            NodeInfo remote = new NodeInfo(rid, presp.getThisIp(), presp.getThisPort());
//            routingTable.update(remote);
//        } catch (Exception timeout) {
//            System.out.println("[DHTNode " + myPeerIdB64 + "] Ping timeout (no PONG).");
//        } finally {
//            udpSocket.setSoTimeout(0);
//        }
//    }
//
//    /**
//     * Sends a FindNodeRequest to a specific peer, waits for FindNodeResponse, updates RT with both sides.
//     */
//    private List<NodeInfo> sendFindNode(String targetPeerIdB64, String targetIp, int targetPort) throws Exception {
//        FindNodeRequest fnreq = new FindNodeRequest(
//                myPeerIdB64,            // who’s asking
//                targetPeerIdB64,        // the target we want to find
//                myIp, myPort
//        );
//        byte[] out = mapper.writeValueAsBytes(fnreq);
//        DatagramPacket pkt = new DatagramPacket(
//                out, out.length,
//                InetAddress.getByName(targetIp), targetPort
//        );
//        udpSocket.send(pkt);
//        System.out.printf("[DHTNode %s] Sent FIND_NODE(target=%s) to %s:%d%n",
//                myPeerIdB64, targetPeerIdB64, targetIp, targetPort);
//
//        // Wait for response
//        udpSocket.setSoTimeout(2000);
//        try {
//            byte[] buf = new byte[MAX_PACKET_SIZE];
//            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
//            udpSocket.receive(respPkt);
//            String respJson = new String(respPkt.getData(), 0, respPkt.getLength());
//            FindNodeResponse fnresp = mapper.readValue(respJson, FindNodeResponse.class);
//
//            System.out.printf("[DHTNode %s] Received FIND_NODE_RESP from %s (respPeerId=%s), %d peers returned%n",
//                    myPeerIdB64,
//                    fnresp.getResponderPeerIdBase64(),
//                    fnresp.getResponderPeerIdBase64(),
//                    fnresp.getClosestPeers().size()
//            );
//
//            // Update RT with the responder
//            byte[] respId = PeerIdUtil.decodeFromBase64(fnresp.getResponderPeerIdBase64());
//            NodeInfo responderInfo = new NodeInfo(respId, targetIp, targetPort);
//            routingTable.update(responderInfo);
//
//            // Convert all returned NodeInfoDTO → NodeInfo, update RT with each, collect in list
//            List<NodeInfo> result = new ArrayList<>();
//            for (NodeInfoDTO dto : fnresp.getClosestPeers()) {
//                NodeInfo ni = dto.toNodeInfo();
//                routingTable.update(ni);
//                result.add(ni);
//            }
//            return result;
//        } catch (Exception timeout) {
//            System.out.println("[DHTNode " + myPeerIdB64 + "] FIND_NODE timeout (no response).");
//            return Collections.emptyList();
//        } finally {
//            udpSocket.setSoTimeout(0);
//        }
//    }
//
//    /**
//     * Iterative Lookup (Kademlia style) to find up to K closest nodes to targetPeerId.
//     *  1. Let shortlist = RT.findClosest(target, ALPHA)
//     *  2. In parallel (up to ALPHA), send FIND_NODE to each.
//     *  3. Gather all returned peers, merge them into shortlist (uniquely).
//     *  4. Repeat: pick next ALPHA closest unqueried, until no closer appear.
//     */
//    private void iterativeFindNode(String targetPeerIdB64) throws Exception {
//        byte[] targetBytes = PeerIdUtil.decodeFromBase64(targetPeerIdB64);
//
//        System.out.printf("\n[DHTNode %s] Starting iterative lookup for %s%n", myPeerIdB64, targetPeerIdB64);
//        // 1. Initial shortlist: get ALPHA closest from RT
//        List<NodeInfo> shortlist = routingTable.findClosest(targetBytes, ALPHA);
//        Set<String> queried = new HashSet<>(); // track peerIdBase64 we already asked
//        boolean anyCloser;
//
//        do {
//            anyCloser = false;
//            // a) Pick up to ALPHA unqueried from shortlist
//            List<NodeInfo> toQuery = new ArrayList<>();
//            for (NodeInfo ni : shortlist) {
//                if (toQuery.size() >= ALPHA) break;
//                if (!queried.contains(ni.getPeerIdBase64())) {
//                    toQuery.add(ni);
//                }
//            }
//            if (toQuery.isEmpty()) break;
//
//            // b) Send FIND_NODE to each in parallel (we'll do sequentially here for simplicity)
//            for (NodeInfo peer : toQuery) {
//                queried.add(peer.getPeerIdBase64());
//                List<NodeInfo> returned = sendFindNode(targetPeerIdB64, peer.getIp(), peer.getPort());
//                // c) Merge returned into shortlist if they are closer than furthest in shortlist
//                for (NodeInfo rn : returned) {
//                    if (!shortlist.contains(rn)) {
//                        shortlist.add(rn);
//                    }
//                }
//            }
//
//            // d) Sort shortlist by XOR distance to target, keep only top K
//            shortlist.sort(Comparator.comparing(n -> n.distanceTo(new NodeInfo(targetBytes, "0.0.0.0", 0))));
//            if (shortlist.size() > K) {
//                shortlist = shortlist.subList(0, K);
//            }
//
//            System.out.printf("[DHTNode %s] Shortlist now (%d nodes): %s%n",
//                    myPeerIdB64, shortlist.size(),
//                    shortenNodeList(shortlist)
//            );
//
//            // If any newly added node is strictly closer than previous best, we continue
//            // (for brevity, we just loop a fixed number of times or until no new peers)
//            // In a production system, you’d track distances and only stop when no closer appear.
//            // Here, we do two iterations max:
//            anyCloser = false; // we’ll break after one iteration for this demo
//        } while (anyCloser);
//
//        System.out.println("[DHTNode " + myPeerIdB64 + "] Iterative lookup ended. Final shortlist:");
//        for (NodeInfo ni : shortlist) {
//            System.out.printf("   • %s (distance=%s)%n",
//                    ni.getPeerIdBase64(),
//                    ni.distanceTo(new NodeInfo(targetBytes, "0.0.0.0", 0)).toString(16));
//        }
//        System.out.println();
//    }
//
//    private static String shortenNodeList(List<NodeInfo> ls) {
//        StringBuilder sb = new StringBuilder("[");
//        for (int i = 0; i < Math.min(ls.size(), 5); i++) {
//            sb.append(ls.get(i).getPeerIdBase64(), 0, 6).append("…");
//            if (i < Math.min(ls.size(), 5) - 1) sb.append(", ");
//        }
//        if (ls.size() > 5) sb.append(", …");
//        sb.append("]");
//        return sb.toString();
//    }
//
//    /**
//     * Interactive console:
//     *  - ping <ip> <port>
//     *  - findnode <targetPeerIdBase64>
//     *  - peers
//     *  - rt
//     *  - exit
//     */
//    private void interactiveMode() throws Exception {
//        Scanner sc = new Scanner(System.in);
//        System.out.println("\nCommands:\n" +
//                "  ping <ip> <port>\n" +
//                "  findnode <targetPeerIdBase64>\n" +
//                "  peers          (prints routing table summary)\n" +
//                "  rt             (detailed RT)\n" +
//                "  exit\n");
//
//        while (true) {
//            String line = sc.nextLine().trim();
//            if (line.equalsIgnoreCase("exit")) {
//                System.out.println("Goodbye.");
//                break;
//            }
//            if (line.startsWith("ping ")) {
//                String[] tok = line.split("\\s+");
//                if (tok.length != 3) {
//                    System.out.println("Usage: ping <ip> <port>");
//                    continue;
//                }
//                sendPing(tok[1], Integer.parseInt(tok[2]));
//            } else if (line.startsWith("findnode ")) {
//                String[] tok = line.split("\\s+");
//                if (tok.length != 2) {
//                    System.out.println("Usage: findnode <targetPeerIdBase64>");
//                    continue;
//                }
//                iterativeFindNode(tok[1]);
//            } else if (line.equalsIgnoreCase("peers")) {
//                System.out.println(routingTable);
//            } else if (line.equalsIgnoreCase("rt")) {
//                System.out.println(routingTable);
//            } else {
//                System.out.println("Unknown command: " + line);
//            }
//        }
//        sc.close();
//    }
//
//    public static void main(String[] args) throws Exception {
//        if (args.length != 1) {
//            System.err.println("Usage: java -jar peershare.jar <udpPort>");
//            System.exit(1);
//        }
//        int port = Integer.parseInt(args[0]);
//        DHTNode node = new DHTNode(port);
//
//        // 1. Bootstrap: get peers from bootstrap server → insert into RT
//        //node.bootstrap();
//
//        // 2. Start listener thread (handle PingRequest + FindNodeRequest)
//        node.startListener();
//
//        // 3. Enter interactive CLI
//        node.interactiveMode();
//    }
//}



package com.example.PeerShare.dht;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.PeerShare.util.PeerIdUtil;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * DHTNode (Phase 2, updated bootstrap logic to use the "real‐time" Bootstrap Server):
 *
 * 1. Generates a random 160-bit peerId.
 * 2. Binds UDP socket on given port.
 * 3. bootstrap():
 *      • Send "REGISTER"  → register ourselves with bootstrap.
 *      • Send "GET_PEERS" → fetch the live peer list → insert into RoutingTable.
 *      • Schedule periodic "HEARTBEAT" every HEARTBEAT_INTERVAL_SEC seconds.
 *
 * 4. startListener(): handle PingRequest, FindNodeRequest.
 *    - On Ping: update RT, send PingResponse.
 *    - On FindNode: update RT, compute up to K closest using RT.findClosest(...), send FindNodeResponse.
 *
 * 5. interactiveMode(): commands:
 *    - ping <ip> <port>
 *    - findnode <targetPeerIdBase64>
 *    - peers
 *    - rt (print routing table summary)
 *    - exit
 *
 * 6. iterativeLookup(targetPeerId):
 *    - α = 3 parallel queries.
 *    - At each round, pick α closest unqueried from shortlist, send them FindNodeRequest.
 *    - Collect responses, merge lists, continue until no closer nodes appear.
 */
public class DHTNode {
    private static final String BOOTSTRAP_IP   = "127.0.0.1";
    private static final int    BOOTSTRAP_PORT = 4000;
    private static final int    MAX_PACKET_SIZE = 4096;
    private static final int    ALPHA = 3;            // parallelism factor
    private static final int    K     = KBucket.K;    // bucket size = 20

    // How often (in seconds) to send a HEARTBEAT to bootstrap
    private static final long   HEARTBEAT_INTERVAL_SEC = 30;

    private final byte[]        myPeerId;             // raw 20 bytes
    private final String        myPeerIdB64;          // Base64
    private final String        myIp;
    private final int           myPort;

    private final RoutingTable  routingTable;
    private final DatagramSocket udpSocket;
    private final ObjectMapper   mapper = new ObjectMapper();

    // Scheduler for periodic heartbeats
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DHTNode(int port) throws Exception {
        this.myPeerId     = PeerIdUtil.generateRandomPeerId();
        this.myPeerIdB64  = PeerIdUtil.encodeToBase64(myPeerId);
        this.myIp         = InetAddress.getLocalHost().getHostAddress();
        this.myPort       = port;
        this.routingTable = new RoutingTable(myPeerId);
        this.udpSocket    = new DatagramSocket(myPort);

        System.out.printf("[DHTNode %s] Listening on %s:%d%n", myPeerIdB64, myIp, myPort);
    }

    /**
     * Bootstrap step (updated):
     *
     * 1) Send REGISTER to bootstrap server.
     * 2) Send GET_PEERS to bootstrap server, parse JSON list, insert each into routingTable.
     * 3) Schedule periodic HEARTBEAT every HEARTBEAT_INTERVAL_SEC seconds.
     */
    private void bootstrap() throws Exception {
        // 1) Send REGISTER now
        sendRegisterOrHeartbeat("REGISTER");

        // 2) Immediately fetch peer list: send GET_PEERS
        sendGetPeers();

        // 3) Schedule periodic HEARTBEATs:
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendRegisterOrHeartbeat("HEARTBEAT");
            } catch (Exception e) {
                System.err.printf("[DHTNode %s] Error sending HEARTBEAT: %s%n", myPeerIdB64, e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Sends either a REGISTER or a HEARTBEAT message to the bootstrap server.
     *
     * The JSON payload is:
     *   {
     *     "type":  "<REGISTER or HEARTBEAT>",
     *     "peerId": "<myPeerIdB64>",
     *     "ip":      "<myIp>",
     *     "port":    <myPort>
     *   }
     */
    private void sendRegisterOrHeartbeat(String type) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("type",   type);
        root.put("peerId", myPeerIdB64);
        root.put("ip",     myIp);
        root.put("port",   myPort);

        byte[] out = mapper.writeValueAsBytes(root);
        DatagramPacket pkt = new DatagramPacket(
                out, out.length,
                InetAddress.getByName(BOOTSTRAP_IP),
                BOOTSTRAP_PORT
        );
        udpSocket.send(pkt);
        System.out.printf("[DHTNode %s] Sent %s to bootstrap%n", myPeerIdB64, type);
    }

    /**
     * Sends a GET_PEERS request to the bootstrap server and updates routingTable with the result.
     *
     * The GET_PEERS payload is simply:
     *   { "type": "GET_PEERS" }
     *
     * On receipt of a JSON‐array response, we parse each element (with fields "peerId","ip","port")
     * and insert a new NodeInfo(...) into our routingTable.
     */
    private void sendGetPeers() throws Exception {
        // Build GET_PEERS JSON
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "GET_PEERS");
        byte[] out = mapper.writeValueAsBytes(root);
        DatagramPacket pkt = new DatagramPacket(
                out, out.length,
                InetAddress.getByName(BOOTSTRAP_IP),
                BOOTSTRAP_PORT
        );
        udpSocket.send(pkt);
        System.out.printf("[DHTNode %s] Sent GET_PEERS to bootstrap%n", myPeerIdB64);

        // Wait for response (blocking). We reuse MAX_PACKET_SIZE and socket timeout
        udpSocket.setSoTimeout(2000);
        try {
            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
            udpSocket.receive(respPkt);
            String jsonList = new String(respPkt.getData(), 0, respPkt.getLength());
            System.out.printf("[DHTNode %s] Received peer list: %s%n", myPeerIdB64, jsonList);

            // Parse JSON → List<Map<String,Object>>
            List<Map<String, Object>> peers = mapper.readValue(
                    jsonList, new TypeReference<>() {}
            );

            // For each entry, convert to NodeInfo and insert into routingTable
            for (Map<String, Object> entry : peers) {
                String ip       = (String) entry.get("ip");
                int    port     = ((Number) entry.get("port")).intValue();
                String peerIdB64= (String) entry.get("peerId");
                byte[] peerId   = PeerIdUtil.decodeFromBase64(peerIdB64);
                NodeInfo ni     = new NodeInfo(peerId, ip, port);
                routingTable.update(ni);
            }

            System.out.println("[DHTNode " + myPeerIdB64 + "] RoutingTable after bootstrap:\n" +
                    routingTable);
        } catch (Exception timeout) {
            System.err.printf("[DHTNode %s] GET_PEERS timed out (no response)%n", myPeerIdB64);
        } finally {
            udpSocket.setSoTimeout(0);
        }
    }

    /**
     * Listener thread: handle PingRequest or FindNodeRequest.
     */
    private void startListener() {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[MAX_PACKET_SIZE];
                while (true) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(pkt);
                    String json = new String(pkt.getData(), 0, pkt.getLength());

                    // 1) Try PingRequest
                    try {
                        PingRequest preq = mapper.readValue(json, PingRequest.class);
                        // Update RT
                        byte[] rid = PeerIdUtil.decodeFromBase64(preq.getPeerIdBase64());
                        NodeInfo remote = new NodeInfo(rid, preq.getSenderIp(), preq.getSenderPort());
                        routingTable.update(remote);

                        System.out.printf("[DHTNode %s] Received PING from %s:%d (peerId=%s)%n",
                                myPeerIdB64,
                                preq.getSenderIp(), preq.getSenderPort(), preq.getPeerIdBase64());

                        // Reply with PingResponse
                        PingResponse presp = new PingResponse(
                                preq.getPeerIdBase64(),
                                myPeerIdB64,
                                myIp, myPort
                        );
                        byte[] out = mapper.writeValueAsBytes(presp);
                        DatagramPacket respPkt = new DatagramPacket(
                                out, out.length,
                                InetAddress.getByName(preq.getSenderIp()),
                                preq.getSenderPort()
                        );
                        udpSocket.send(respPkt);
                        continue;
                    } catch (Exception ignored) {
                        // not a PingRequest
                    }

                    // 2) Try FindNodeRequest
                    try {
                        FindNodeRequest fnreq = mapper.readValue(json, FindNodeRequest.class);
                        // Update RT with info about the requester
                        byte[] rid = PeerIdUtil.decodeFromBase64(fnreq.getRequesterPeerIdBase64());
                        NodeInfo requesterInfo = new NodeInfo(rid, fnreq.getRequesterIp(), fnreq.getRequesterPort());
                        routingTable.update(requesterInfo);

                        System.out.printf("[DHTNode %s] Received FIND_NODE(target=%s) from %s:%d%n",
                                myPeerIdB64,
                                fnreq.getTargetPeerIdBase64(),
                                fnreq.getRequesterIp(), fnreq.getRequesterPort()
                        );

                        // Find k closest known peers to targetPeerId
                        byte[] targetBytes = PeerIdUtil.decodeFromBase64(fnreq.getTargetPeerIdBase64());
                        List<NodeInfo> closest = routingTable.findClosest(targetBytes, K);

                        // Convert to DTOs
                        List<NodeInfoDTO> dtoList = new ArrayList<>();
                        for (NodeInfo ni : closest) {
                            dtoList.add(NodeInfoDTO.fromNodeInfo(ni));
                        }

                        // Build response
                        FindNodeResponse fnresp = new FindNodeResponse(
                                fnreq.getRequesterPeerIdBase64(),
                                myPeerIdB64,
                                dtoList
                        );
                        byte[] out = mapper.writeValueAsBytes(fnresp);
                        DatagramPacket respPkt = new DatagramPacket(
                                out, out.length,
                                InetAddress.getByName(fnreq.getRequesterIp()),
                                fnreq.getRequesterPort()
                        );
                        udpSocket.send(respPkt);
                        continue;
                    } catch (Exception ignored) {
                        // not a FindNodeRequest
                    }

                    // else: ignore unknown JSON
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends a PingRequest to a specific peer, waits for PingResponse, updates RT.
     */
    private void sendPing(String targetIp, int targetPort) throws Exception {
        PingRequest preq = new PingRequest(myPeerIdB64, myIp, myPort);
        byte[] out = mapper.writeValueAsBytes(preq);
        DatagramPacket pkt = new DatagramPacket(
                out, out.length,
                InetAddress.getByName(targetIp), targetPort
        );
        udpSocket.send(pkt);
        System.out.printf("[DHTNode %s] Sent PING to %s:%d%n", myPeerIdB64, targetIp, targetPort);

        // Wait for PONG
        udpSocket.setSoTimeout(2000);
        try {
            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
            udpSocket.receive(respPkt);
            PingResponse presp = mapper.readValue(
                    new String(respPkt.getData(), 0, respPkt.getLength()), PingResponse.class
            );
            System.out.printf("[DHTNode %s] Received PONG from %s:%d (peerId=%s)%n",
                    myPeerIdB64,
                    presp.getThisIp(), presp.getThisPort(),
                    presp.getThisPeerIdBase64()
            );
            // Update RT with who replied
            byte[] rid = PeerIdUtil.decodeFromBase64(presp.getThisPeerIdBase64());
            NodeInfo remote = new NodeInfo(rid, presp.getThisIp(), presp.getThisPort());
            routingTable.update(remote);
        } catch (Exception timeout) {
            System.out.println("[DHTNode " + myPeerIdB64 + "] Ping timeout (no PONG).");
        } finally {
            udpSocket.setSoTimeout(0);
        }
    }

    /**
     * Sends a FindNodeRequest to a specific peer, waits for FindNodeResponse, updates RT with both sides.
     */
    private List<NodeInfo> sendFindNode(String targetPeerIdB64, String targetIp, int targetPort) throws Exception {
        FindNodeRequest fnreq = new FindNodeRequest(
                myPeerIdB64,            // who’s asking
                targetPeerIdB64,        // the target we want to find
                myIp, myPort
        );
        byte[] out = mapper.writeValueAsBytes(fnreq);
        DatagramPacket pkt = new DatagramPacket(
                out, out.length,
                InetAddress.getByName(targetIp), targetPort
        );
        udpSocket.send(pkt);
        System.out.printf("[DHTNode %s] Sent FIND_NODE(target=%s) to %s:%d%n",
                myPeerIdB64, targetPeerIdB64, targetIp, targetPort);

        // Wait for response
        udpSocket.setSoTimeout(2000);
        try {
            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
            udpSocket.receive(respPkt);
            String respJson = new String(respPkt.getData(), 0, respPkt.getLength());
            FindNodeResponse fnresp = mapper.readValue(respJson, FindNodeResponse.class);

            System.out.printf("[DHTNode %s] Received FIND_NODE_RESP from %s (respPeerId=%s), %d peers returned%n",
                    myPeerIdB64,
                    fnresp.getResponderPeerIdBase64(),
                    fnresp.getResponderPeerIdBase64(),
                    fnresp.getClosestPeers().size()
            );

            // Update RT with the responder
            byte[] respId = PeerIdUtil.decodeFromBase64(fnresp.getResponderPeerIdBase64());
            NodeInfo responderInfo = new NodeInfo(respId, targetIp, targetPort);
            routingTable.update(responderInfo);

            // Convert all returned NodeInfoDTO → NodeInfo, update RT with each, collect in list
            List<NodeInfo> result = new ArrayList<>();
            for (NodeInfoDTO dto : fnresp.getClosestPeers()) {
                NodeInfo ni = dto.toNodeInfo();
                routingTable.update(ni);
                result.add(ni);
            }
            return result;
        } catch (Exception timeout) {
            System.out.println("[DHTNode " + myPeerIdB64 + "] FIND_NODE timeout (no response).");
            return Collections.emptyList();
        } finally {
            udpSocket.setSoTimeout(0);
        }
    }

    /**
     * Iterative Lookup (Kademlia style) to find up to K closest nodes to targetPeerId.
     *  1. Let shortlist = RT.findClosest(target, ALPHA)
     *  2. In parallel (up to ALPHA), send FIND_NODE to each.
     *  3. Gather all returned peers, merge them into shortlist (uniquely).
     *  4. Repeat: pick next ALPHA closest unqueried, until no closer appear.
     */
    private void iterativeFindNode(String targetPeerIdB64) throws Exception {
        byte[] targetBytes = PeerIdUtil.decodeFromBase64(targetPeerIdB64);

        System.out.printf("\n[DHTNode %s] Starting iterative lookup for %s%n", myPeerIdB64, targetPeerIdB64);
        // 1. Initial shortlist: get ALPHA closest from RT
        List<NodeInfo> shortlist = routingTable.findClosest(targetBytes, ALPHA);
        Set<String> queried = new HashSet<>(); // track peerIdBase64 we already asked
        boolean anyCloser;

        do {
            anyCloser = false;
            // a) Pick up to ALPHA unqueried from shortlist
            List<NodeInfo> toQuery = new ArrayList<>();
            for (NodeInfo ni : shortlist) {
                if (toQuery.size() >= ALPHA) break;
                if (!queried.contains(ni.getPeerIdBase64())) {
                    toQuery.add(ni);
                }
            }
            if (toQuery.isEmpty()) break;

            // b) Send FIND_NODE to each in parallel (we'll do sequentially here for simplicity)
            for (NodeInfo peer : toQuery) {
                queried.add(peer.getPeerIdBase64());
                List<NodeInfo> returned = sendFindNode(targetPeerIdB64, peer.getIp(), peer.getPort());
                // c) Merge returned into shortlist if they are closer than furthest in shortlist
                for (NodeInfo rn : returned) {
                    if (!shortlist.contains(rn)) {
                        shortlist.add(rn);
                    }
                }
            }

            // d) Sort shortlist by XOR distance to target, keep only top K
            shortlist.sort(Comparator.comparing(n -> n.distanceTo(new NodeInfo(targetBytes, "0.0.0.0", 0))));
            if (shortlist.size() > K) {
                shortlist = shortlist.subList(0, K);
            }

            System.out.printf("[DHTNode %s] Shortlist now (%d nodes): %s%n",
                    myPeerIdB64, shortlist.size(),
                    shortenNodeList(shortlist)
            );

            // In this demo, we break after one iteration
            anyCloser = false;
        } while (anyCloser);

        System.out.println("[DHTNode " + myPeerIdB64 + "] Iterative lookup ended. Final shortlist:");
        for (NodeInfo ni : shortlist) {
            System.out.printf("   • %s (distance=%s)%n",
                    ni.getPeerIdBase64(),
                    ni.distanceTo(new NodeInfo(targetBytes, "0.0.0.0", 0)).toString(16));
        }
        System.out.println();
    }

    private static String shortenNodeList(List<NodeInfo> ls) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(ls.size(), 5); i++) {
            sb.append(ls.get(i).getPeerIdBase64(), 0, 6).append("…");
            if (i < Math.min(ls.size(), 5) - 1) sb.append(", ");
        }
        if (ls.size() > 5) sb.append(", …");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Interactive console:
     *  - ping <ip> <port>
     *  - findnode <targetPeerIdBase64>
     *  - peers
     *  - rt
     *  - exit
     */
    private void interactiveMode() throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("\nCommands:\n" +
                "  ping <ip> <port>\n" +
                "  findnode <targetPeerIdBase64>\n" +
                "  peers          (prints routing table summary)\n" +
                "  rt             (detailed RT)\n" +
                "  exit\n");

        while (true) {
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye.");
                break;
            }
            if (line.startsWith("ping ")) {
                String[] tok = line.split("\\s+");
                if (tok.length != 3) {
                    System.out.println("Usage: ping <ip> <port>");
                    continue;
                }
                sendPing(tok[1], Integer.parseInt(tok[2]));
            } else if (line.startsWith("findnode ")) {
                String[] tok = line.split("\\s+");
                if (tok.length != 2) {
                    System.out.println("Usage: findnode <targetPeerIdBase64>");
                    continue;
                }
                iterativeFindNode(tok[1]);
            } else if (line.equalsIgnoreCase("peers")) {
                System.out.println(routingTable);
            } else if (line.equalsIgnoreCase("rt")) {
                System.out.println(routingTable);
            } else {
                System.out.println("Unknown command: " + line);
            }
        }
        sc.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar peershare.jar <udpPort>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        DHTNode node = new DHTNode(port);

        // 1. Bootstrap: register + fetch peers + schedule heartbeats
        node.bootstrap();

        // 2. Start listener thread (handle PingRequest + FindNodeRequest)
        node.startListener();

        // 3. Enter interactive CLI
        node.interactiveMode();

        // Shutdown scheduler on exit
        node.scheduler.shutdownNow();
    }
}


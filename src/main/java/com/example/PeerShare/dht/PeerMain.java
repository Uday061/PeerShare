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
// * PeerMain (Phase 1):
// * 1. Generates random 160-bit ID.
// * 2. Binds UDP socket on port args[0].
// * 3. HELLO → Bootstrap (127.0.0.1:4000), parse peer list.
// * 4. Start listener thread: on PingRequest, reply PingResponse.
// * 5. If user calls " –ping <ip> <port>", send PingRequest there.
// */
//
//public class PeerMain {
//
//    private static final String BOOTSTRAP_IP = "127.0.0.1";
//    private static final int BOOTSTRAP_PORT = 4000;
//    private static final int MAX_PACKET_SIZE = 2048; // enough for our JSON
//
//    private final String myPeerIdB64;           // Base64 string
//    private final InetAddress myIp;
//    private final int myPort;
//
//    // Known peers discovered from bootstrap
//    private final Set<Map<String, Object>> knownPeers = Collections.synchronizedSet(new HashSet<>());
//
//    private final ObjectMapper mapper = new ObjectMapper();
//    private final DatagramSocket udpSocket;
//
//    public PeerMain(int port) throws Exception {
//        // 20 bytes
//        byte[] myPeerId = PeerIdUtil.generateRandomPeerId();
//        this.myPeerIdB64 = PeerIdUtil.encodeToBase64(myPeerId);
//        this.myIp = InetAddress.getLocalHost();
//        this.myPort = port;
//        this.udpSocket = new DatagramSocket(myPort);
//        System.out.printf("[Peer %s] Listening on %s:%d%n", myPeerIdB64, myIp.getHostAddress(), myPort);
//    }
//
//    /**
//     * Ping the bootstrap server with "HELLO", parse JSON list, store in knownPeers.
//     */
//    private void bootstrap() throws Exception {
//        String hello = "HELLO";
//        byte[] buf = hello.getBytes();
//        DatagramPacket pkt = new DatagramPacket(
//                buf, buf.length,
//                InetAddress.getByName(BOOTSTRAP_IP), BOOTSTRAP_PORT
//        );
//        udpSocket.send(pkt);
//        System.out.println("[Peer " + myPeerIdB64 + "] Sent HELLO to bootstrap.");
//
//        // Wait for reply once (blocking)
//        byte[] recvBuf = new byte[MAX_PACKET_SIZE];
//        DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
//        udpSocket.receive(recvPkt);
//        int len = recvPkt.getLength();
//        String jsonList = new String(recvPkt.getData(), 0, len);
//        System.out.println("[Peer " + myPeerIdB64 + "] Received peer list: " + jsonList);
//
//        // Parse into List<Map<String,Object>>
//        List<Map<String, Object>> peers = mapper.readValue(
//                jsonList, new TypeReference<List<Map<String, Object>>>() {}
//        );
//        knownPeers.addAll(peers);
//        System.out.println("[Peer " + myPeerIdB64 + "] Known peers after bootstrap: " + knownPeers);
//    }
//
//    /**
//     * Continuously listens for incoming UDP packets.
//     * - If PingRequest: replies with PingResponse.
//     * - Otherwise: ignores.
//     */
//    private void startListener() {
//        Thread t = new Thread(() -> {
//            try {
//                byte[] buf = new byte[MAX_PACKET_SIZE];
//                while (true) {
//                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
//                    udpSocket.receive(pkt);
//
//                    String incomingJson = new String(pkt.getData(), 0, pkt.getLength());
//                    // Try to parse as PingRequest
//                    try {
//                        PingRequest preq = mapper.readValue(incomingJson, PingRequest.class);
//                        System.out.printf("[Peer %s] Received PING from %s:%d (peerId=%s)%n",
//                                myPeerIdB64,
//                                preq.getSenderIp(), preq.getSenderPort(),
//                                preq.getPeerIdBase64()
//                        );
//                        // Add to known peers set
//                        Map<String, Object> m = new HashMap<>();
//                        m.put("ip", preq.getSenderIp());
//                        m.put("port", preq.getSenderPort());
//                        m.put("peerId", preq.getPeerIdBase64());
//                        knownPeers.add(m);
//
//                        // Respond with a PingResponse
//                        PingResponse presp = new PingResponse(
//                                preq.getPeerIdBase64(),
//                                myPeerIdB64,
//                                myIp.getHostAddress(),
//                                myPort
//                        );
//                        byte[] respBytes = mapper.writeValueAsBytes(presp);
//                        DatagramPacket respPkt = new DatagramPacket(
//                                respBytes, respBytes.length,
//                                InetAddress.getByName(preq.getSenderIp()),
//                                preq.getSenderPort()
//                        );
//                        udpSocket.send(respPkt);
//                        System.out.printf("[Peer %s] Sent PONG to %s:%d%n",
//                                myPeerIdB64,
//                                preq.getSenderIp(), preq.getSenderPort()
//                        );
//                    } catch (Exception e) {
//                        // Not a PingRequest (could be garbage)—ignore
//                    }
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
//     * Allows user to manually ping another peer:
//     * Usage: ping <ip> <port>
//     */
//    private void interactivePing() throws Exception {
//        Scanner sc = new Scanner(System.in);
//        System.out.println("[Peer " + myPeerIdB64 + "] Enter commands. Type 'ping <ip> <port>' or 'peers'. Type 'exit' to quit.");
//        while (true) {
//            String line = sc.nextLine().trim();
//            if (line.equalsIgnoreCase("exit")) {
//                System.out.println("Bye.");
//                break;
//            }
//            if (line.startsWith("ping ")) {
//                String[] tok = line.split("\\s+");
//                if (tok.length != 3) {
//                    System.out.println("Usage: ping <ip> <port>");
//                    continue;
//                }
//                String targetIp = tok[1];
//                int targetPort = Integer.parseInt(tok[2]);
//
//                // Build PingRequest
//                PingRequest preq = new PingRequest(myPeerIdB64, myIp.getHostAddress(), myPort);
//                byte[] reqBytes = mapper.writeValueAsBytes(preq);
//
//                DatagramPacket reqPkt = new DatagramPacket(
//                        reqBytes, reqBytes.length,
//                        InetAddress.getByName(targetIp), targetPort
//                );
//                udpSocket.send(reqPkt);
//                System.out.printf("[Peer %s] Sent PING to %s:%d%n", myPeerIdB64, targetIp, targetPort);
//
//                // Wait one packet for PingResponse (with a 3s timeout)
//                udpSocket.setSoTimeout(3000);
//                try {
//                    byte[] buf = new byte[MAX_PACKET_SIZE];
//                    DatagramPacket rspPkt = new DatagramPacket(buf, buf.length);
//                    udpSocket.receive(rspPkt);
//                    PingResponse presp = mapper.readValue(
//                            new String(rspPkt.getData(), 0, rspPkt.getLength()), PingResponse.class
//                    );
//                    System.out.printf("[Peer %s] Received PONG from %s:%d (peerId=%s)%n",
//                            myPeerIdB64,
//                            presp.getThisIp(), presp.getThisPort(),
//                            presp.getThisPeerIdBase64()
//                    );
//                } catch (Exception timeoutEx) {
//                    System.out.println("[Peer " + myPeerIdB64 + "] No PONG received (timeout).");
//                } finally {
//                    udpSocket.setSoTimeout(0); // back to blocking
//                }
//            } else if (line.equalsIgnoreCase("peers")) {
//                System.out.println("[Peer " + myPeerIdB64 + "] Known peers = " + knownPeers);
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
//        PeerMain peer = new PeerMain(port);
//
//        // 1. Bootstrap (HELLO → get JSON peer list)
//        peer.bootstrap();
//
//        // 2. Start listener thread (to handle incoming PING)
//        peer.startListener();
//
//        // 3. Enter interactive console to ping other peers
//        peer.interactivePing();
//    }
//}

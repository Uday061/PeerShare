// src/main/java/com/PeerShare/dht/NodeInfo.java
package com.example.PeerShare.dht;

import com.example.PeerShare.util.PeerIdUtil;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Represents a remote peer: (peerId - 160-bit), IP, and port.
 */
public class NodeInfo {
    private final byte[] peerId; // length = 20
    private final String ip;
    private final int port;

    public NodeInfo(byte[] peerId, String ip, int port) {
        if (peerId.length != 20) throw new IllegalArgumentException("peerId must be 20 bytes");
        this.peerId = peerId.clone();
        this.ip = ip;
        this.port = port;
    }

    /** Base64-URL representation (for JSON/logging). */
    public String getPeerIdBase64() {
        return PeerIdUtil.encodeToBase64(peerId);
    }

    public byte[] getPeerId() {
        return peerId.clone();
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    /**
     * Computes XOR distance between this.peerId and other.peerId, returns as BigInteger.
     * (BigInteger allows us to find which bucket the peer belongs to.)
     */
    public BigInteger distanceTo(NodeInfo other) {
        byte[] a = this.peerId;
        byte[] b = other.peerId;
        byte[] xor = new byte[20];
        for (int i = 0; i < 20; i++) {
            xor[i] = (byte) (a[i] ^ b[i]);
        }
        return new BigInteger(1, xor); // always positive
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NodeInfo)) return false;
        NodeInfo other = (NodeInfo) o;
        return Arrays.equals(this.peerId, other.peerId);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(peerId);
    }

    @Override
    public String toString() {
        return String.format("NodeInfo{id=%s, %s:%d}", getPeerIdBase64(), ip, port);
    }
}

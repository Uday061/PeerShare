
package com.example.PeerShare.dht;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 160 K-buckets, one per (log2 distance) index.
 */
public class RoutingTable {
    private final byte[] myPeerId;      // 20 bytes
    private final KBucket[] buckets = new KBucket[160];

    public RoutingTable(byte[] myPeerId) {
        this.myPeerId = myPeerId.clone();
        for (int i = 0; i < 160; i++) {
            buckets[i] = new KBucket(i, myPeerId);
        }
    }

    /**
     * Update the routing table with a newly seen NodeInfo.
     * If nodeId == me, ignore.
     * Otherwise: compute bucket index and insert into that bucket.
     */
    public void update(NodeInfo n) {
        BigInteger distance = computeDistance(n.getPeerId());
        if (distance.equals(BigInteger.ZERO)) {
            // that was me → ignore
            return;
        }
        int bucketIndex = bucketIndexFor(distance);
        buckets[bucketIndex].insert(n);
    }

    /**
     * Returns the KBucket index (0..159) for a given distance.
     * Let d = distance.bitLength() – 1 (0..159). Then bucketIndex = d.
     */
    private int bucketIndexFor(BigInteger distance) {
        int bitLen = distance.bitLength(); // 1..160
        int d = bitLen - 1;                 // 0..159  (0 means MSB at position 0 (i.e. distance ≥ 2^159))
        return d;
    }

    /**
     * XOR distance from me to the given peerId.
     */
    private BigInteger computeDistance(byte[] peerId) {
        byte[] xor = new byte[20];
        for (int i = 0; i < 20; i++) {
            xor[i] = (byte) (myPeerId[i] ^ peerId[i]);
        }
        return new BigInteger(1, xor);
    }

    /**
     * Finds up to 'count' NodeInfos in this routing table that are closest (XOR) to the given targetId.
     */
    public List<NodeInfo> findClosest(byte[] targetPeerId, int count) {
        PriorityQueue<NodeInfoDistance> pq = new PriorityQueue<>(Comparator.comparing(o -> o.distance));
        // Iterate over all buckets → all nodes
        for (KBucket bucket : buckets) {
            for (NodeInfo ni : bucket.getNodes()) {
                BigInteger dist = ni.distanceTo(new NodeInfo(targetPeerId, "0.0.0.0", 0));
                pq.add(new NodeInfoDistance(ni, dist));
            }
        }
        List<NodeInfo> result = new ArrayList<>();
        while (!pq.isEmpty() && result.size() < count) {
            result.add(pq.poll().node);
        }
        return result;
    }

    /**
     * Internal helper: pairs a NodeInfo with its XOR distance to target.
     */
    private static class NodeInfoDistance {
        final NodeInfo node;
        final BigInteger distance;
        NodeInfoDistance(NodeInfo n, BigInteger dist) {
            this.node = n;
            this.distance = dist;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RoutingTable:\n");
        for (int i = 0; i < 160; i++) {
            int size = buckets[i].getNodes().size();
            if (size > 0) {
                sb.append(String.format("  Bucket[%d] (size=%d)\n", i, size));
            }
        }
        return sb.toString();
    }
}

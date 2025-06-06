
package com.example.PeerShare.dht;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A single K-bucket (max size K = 20).
 * Holds NodeInfos whose XOR distance from us has its highest bit at a fixed index.
 */
public class KBucket {
    public static final int K = 20;

    private final int bucketIndex;       // 0..159
    private final byte[] myPeerId;       // used to prevent inserting ourselves
    private final LinkedList<NodeInfo> nodes = new LinkedList<>();

    public KBucket(int bucketIndex, byte[] myPeerId) {
        this.bucketIndex = bucketIndex;
        this.myPeerId = myPeerId.clone();
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    /**
     * Returns an immutable snapshot of the nodes in this bucket.
     */
    public List<NodeInfo> getNodes() {
        return List.copyOf(nodes);
    }

    /**
     * Insert or update a NodeInfo:
     * 1. If peerId == myPeerId: ignore.
     * 2. If peer already exists, move it to tail (most recently seen).
     * 3. Else if size < K: just append at tail.
     * 4. Else if size == K: drop head, then append at tail.
     */
    public synchronized void insert(NodeInfo n) {
        byte[] peerId = n.getPeerId();
        if (java.util.Arrays.equals(peerId, myPeerId)) {
            return; // do not insert ourselves
        }

        // Check if already present
        Iterator<NodeInfo> it = nodes.iterator();
        while (it.hasNext()) {
            NodeInfo existing = it.next();
            if (java.util.Arrays.equals(existing.getPeerId(), peerId)) {
                // Move to tail
                it.remove();
                nodes.addLast(n);
                return;
            }
        }

        // Not present
        if (nodes.size() < K) {
            nodes.addLast(n);
        } else {
            // Bucket is full: drop least‐recently‐seen (head) and append new at tail
            nodes.removeFirst();
            nodes.addLast(n);
        }
    }

    @Override
    public String toString() {
        return String.format("KBucket(index=%d, size=%d)", bucketIndex, nodes.size());
    }
}

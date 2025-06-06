package com.example.PeerShare.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FIND_NODE RPC: asks the receiver to return up to K closest peers to 'targetPeerIdBase64'.
 */
public class FindNodeRequest {
    private final String requesterPeerIdBase64;
    private final String targetPeerIdBase64;
    private final String requesterIp;
    private final int requesterPort;

    @JsonCreator
    public FindNodeRequest(
            @JsonProperty("requesterPeerIdBase64") String requesterPeerIdBase64,
            @JsonProperty("targetPeerIdBase64") String targetPeerIdBase64,
            @JsonProperty("requesterIp") String requesterIp,
            @JsonProperty("requesterPort") int requesterPort
    ) {
        this.requesterPeerIdBase64 = requesterPeerIdBase64;
        this.targetPeerIdBase64 = targetPeerIdBase64;
        this.requesterIp = requesterIp;
        this.requesterPort = requesterPort;
    }

    public String getRequesterPeerIdBase64() {
        return requesterPeerIdBase64;
    }

    public String getTargetPeerIdBase64() {
        return targetPeerIdBase64;
    }

    public String getRequesterIp() {
        return requesterIp;
    }

    public int getRequesterPort() {
        return requesterPort;
    }
}

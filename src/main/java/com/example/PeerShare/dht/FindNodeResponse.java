
package com.example.PeerShare.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * FIND_NODE Response: returns up to K closest peers to the requested targetID.
 */
public class FindNodeResponse {
    private final String requesterPeerIdBase64;  // echo
    private final String responderPeerIdBase64;  // our ID
    private final List<NodeInfoDTO> closestPeers; // up to K = 20

    @JsonCreator
    public FindNodeResponse(
            @JsonProperty("requesterPeerIdBase64") String requesterPeerIdBase64,
            @JsonProperty("responderPeerIdBase64") String responderPeerIdBase64,
            @JsonProperty("closestPeers") List<NodeInfoDTO> closestPeers
    ) {
        this.requesterPeerIdBase64 = requesterPeerIdBase64;
        this.responderPeerIdBase64 = responderPeerIdBase64;
        this.closestPeers = closestPeers;
    }

    public String getRequesterPeerIdBase64() {
        return requesterPeerIdBase64;
    }

    public String getResponderPeerIdBase64() {
        return responderPeerIdBase64;
    }

    public List<NodeInfoDTO> getClosestPeers() {
        return closestPeers;
    }
}

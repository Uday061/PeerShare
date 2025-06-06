// src/main/java/com/PeerShare/dht/PingResponse.java
package com.example.PeerShare.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A PONG message: echoes back the requester’s peerId, and includes this peer’s own ID & address.
 */
public class PingResponse {
    private final String requesterPeerIdBase64;
    private final String thisPeerIdBase64;
    private final String thisIp;
    private final int thisPort;

    @JsonCreator
    public PingResponse(
            @JsonProperty("requesterPeerIdBase64") String requesterPeerIdBase64,
            @JsonProperty("thisPeerIdBase64") String thisPeerIdBase64,
            @JsonProperty("thisIp") String thisIp,
            @JsonProperty("thisPort") int thisPort
    ) {
        this.requesterPeerIdBase64 = requesterPeerIdBase64;
        this.thisPeerIdBase64 = thisPeerIdBase64;
        this.thisIp = thisIp;
        this.thisPort = thisPort;
    }

    public String getRequesterPeerIdBase64() {
        return requesterPeerIdBase64;
    }

    public String getThisPeerIdBase64() {
        return thisPeerIdBase64;
    }

    public String getThisIp() {
        return thisIp;
    }

    public int getThisPort() {
        return thisPort;
    }
}

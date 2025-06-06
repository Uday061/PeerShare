
package com.example.PeerShare.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A PING message: who you are (peerId), your IP and port.
 */
public class PingRequest {
    private final String peerIdBase64;
    private final String senderIp;
    private final int senderPort;

    @JsonCreator
    public PingRequest(
            @JsonProperty("peerIdBase64") String peerIdBase64,
            @JsonProperty("senderIp") String senderIp,
            @JsonProperty("senderPort") int senderPort
    ) {
        this.peerIdBase64 = peerIdBase64;
        this.senderIp = senderIp;
        this.senderPort = senderPort;
    }

    public String getPeerIdBase64() {
        return peerIdBase64;
    }

    public String getSenderIp() {
        return senderIp;
    }

    public int getSenderPort() {
        return senderPort;
    }
}
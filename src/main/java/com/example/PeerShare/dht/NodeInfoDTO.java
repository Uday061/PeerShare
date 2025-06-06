
package com.example.PeerShare.dht;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON‐friendly DTO for NodeInfo: (peerIdBase64, ip, port)
 */
public class NodeInfoDTO {
    private final String peerIdBase64;
    private final String ip;
    private final int port;

    @JsonCreator
    public NodeInfoDTO(
            @JsonProperty("peerIdBase64") String peerIdBase64,
            @JsonProperty("ip") String ip,
            @JsonProperty("port") int port
    ) {
        this.peerIdBase64 = peerIdBase64;
        this.ip = ip;
        this.port = port;
    }

    public String getPeerIdBase64() {
        return peerIdBase64;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public static NodeInfoDTO fromNodeInfo(NodeInfo ni) {
        return new NodeInfoDTO(ni.getPeerIdBase64(), ni.getIp(), ni.getPort());
    }

    public NodeInfo toNodeInfo() {
        byte[] pid = com.example.PeerShare.util.PeerIdUtil.decodeFromBase64(peerIdBase64);
        return new NodeInfo(pid, ip, port);
    }
}

# PeerShare

**A resilient, encrypted, erasure-coded P2P file-sharing platform in Java/Spring Boot. 
It works on the principles of kademlia and DHT to to effectively route to the required peer under logN complexity.
File chunking and multithreading enchances the performance even further.**

## Project Structure

```plaintext
PeerShare/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── HELP.md
├── .gitignore
├── .gitattributes
├── .idea/                           ← IntelliJ project files (ignore for VCS)
├── .mvn/                            ← Maven wrapper (ignore for VCS)
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           └── peershare
│   │   │               ├── PeerShareApplication.java       ← Spring Boot entry point
│   │   │               │
│   │   │               ├── config/                         ← Spring & application configuration
│   │   │               │   ├── AppConfig.java              ← Bean definitions, property binding
│   │   │               │   └── WebSocketConfig.java        ← WebSocket/STOMP configuration
│   │   │               │
│   │   │               ├── util/                           ← General-purpose helper classes
│   │   │               │   ├── PeerIdUtil.java             ← Generate / encode/decode 160-bit IDs
│   │   │               │   ├── HashUtil.java               ← SHA-1 hashing for file chunks
│   │   │               │   ├── NetworkUtil.java            ← IP/port helpers, address parsing
│   │   │               │   └── JsonRpcUtil.java            ← Common JSON (de)serialization logic
│   │   │               │
│   │   │               ├── transport/                      ← Low-level networking (UDP/TCP/libP2P)
│   │   │               │   ├── UdpTransport.java           ← Wrapper around java.net.DatagramSocket
│   │   │               │   ├── TcpTransport.java           ← Wrapper around java.net.Socket / ServerSocket
│   │   │               │   ├── Libp2pNode.java              ← Helper for initializing a libP2P Host
│   │   │               │   └── TransportFactory.java       ← Choose between UDP vs. libP2P transports
│   │   │               │
│   │   │               ├── bootstrap/                      ← Bootstrapping & initial peer discovery
│   │   │               │   ├── BootstrapServer.java        ← UDP “HELLO” server sending seed peer list
│   │   │               │   └── BootstrapClient.java        ← “HELLO” client that fetches seed peers
│   │   │               │
│   │   │               ├── dht/                            ← Core Kademlia DHT logic & JSON-RPCs
│   │   │               │   ├── NodeInfo.java                ← (peerId, IP, port) + XOR-distance calculation
│   │   │               │   ├── NodeInfoDTO.java             ← JSON-friendly form of NodeInfo
│   │   │               │   ├── KBucket.java                 ← Single bucket (max K entries, LRU eviction)
│   │   │               │   ├── RoutingTable.java            ← 160 buckets + update() / findClosest()
│   │   │               │   ├── PingRequest.java             ← JSON RPC: PING
│   │   │               │   ├── PingResponse.java            ← JSON RPC: PONG
│   │   │               │   ├── FindNodeRequest.java         ← JSON RPC: FIND_NODE(targetID)
│   │   │               │   ├── FindNodeResponse.java        ← JSON RPC: list of closest peers
│   │   │               │   ├── FindValueRequest.java        ← JSON RPC: FIND_VALUE(chunkID) (Phase 3+)
│   │   │               │   ├── FindValueResponse.java       ← JSON RPC: returned data or peer list (Phase 3+)
│   │   │               │   ├── DhtNode.java                 ← Combines transport + routing + RPC listener
│   │   │               │   └── IterativeLookup.java         ← α-parallel lookup logic (optional helper)
│   │   │               │
│   │   │               ├── storage/                        ← File chunking, metadata, local persistence
│   │   │               │   ├── Chunker.java                 ← Split file → fixed-size byte[] shards
│   │   │               │   ├── ChunkMetadata.java           ← Metadata: chunkID (SHA-1), length, owner, etc.
│   │   │               │   ├── LocalStorage.java            ← Write/read raw chunk bytes to disk
│   │   │               │   ├── DhtMetadataStore.java        ← In-memory map: chunkID → List<NodeInfo>
│   │   │               │   └── FileManifest.java            ← Metadata grouping chunkIDs into a file
│   │   │               │
│   │   │               ├── security/                       ← Encryption, key-exchange, signing/verification
│   │   │               │   ├── KeyManager.java              ← Load / generate RSA key pair on startup
│   │   │               │   ├── AesUtil.java                 ← AES encrypt/decrypt byte[] (GCM/NoPadding)
│   │   │               │   ├── RsaUtil.java                 ← RSA encrypt/decrypt + key serialization
│   │   │               │   ├── SignatureUtil.java           ← (Optional) Sign / verify file or manifest
│   │   │               │   └── SecurityConstants.java       ← RSA key size, AES key length, etc.
│   │   │               │
│   │   │               ├── erasure/                        ← Reed-Solomon erasure coding for resilience
│   │   │               │   ├── ErasureCodec.java            ← Wraps Backblaze RS encode/decode calls
│   │   │               │   ├── ErasureMetadata.java         ← Tracks (dataShards, parityShards) per file
│   │   │               │   └── ShardManager.java            ← Produce RS shards; reassemble when downloading
│   │   │               │
│   │   │               ├── nat/                            ← NAT traversal (STUN client, TURN fallback)
│   │   │               │   ├── StunClient.java              ← Use a STUN library to discover public IP/port
│   │   │               │   ├── TurnClient.java              ← (Optional) Relay via TURN if hole-punch fails
│   │   │               │   └── NatTraversalService.java     ← High-level façade for “getExternalAddress()”
│   │   │               │
│   │   │               ├── reputation/                     ← Peer scoring & trust management
│   │   │               │   ├── ReputationManager.java       ← Record success/failure, compute trust scores
│   │   │               │   ├── TrustComparator.java         ← Compare NodeInfo by descending trust
│   │   │               │   └── ReputationConstants.java     ← Settings: window sizes, decay rates, etc.
│   │   │               │
│   │   │               ├── dashboard/                      ← Spring WebSocket + REST endpoints & UI
│   │   │               │   ├── controller/
│   │   │               │   │   ├── PeerWsController.java    ← @Controller for WebSocket peer events
│   │   │               │   │   └── DashboardRestController.java ← @RestController for stats & peer info
│   │   │               │   ├── service/
│   │   │               │   │   ├── EventBus.java            ← Publishes DHT events (join/leave, lookups, etc.)
│   │   │               │   │   └── PeerService.java         ← Provides current peer list, trust scores, metrics
│   │   │               │   ├── model/
│   │   │               │   │   └── PeerEvent.java           ← POJO describing a peer‐related event for WS
│   │   │               │   └── websocket/
│   │   │               │       └── WsConfig.java            ← STOMP/WebSocket configuration (allowed origins, etc.)
│   │   │               │
│   │   │               └── cli/                            ← (Optional) A headless command‐line interface
│   │   │                   ├── CliParser.java               ← Parse commands: “share <path>”, “get <id>”, etc.
│   │   │                   └── CliRunner.java               ← Spring Boot CommandLineRunner for CLI mode
│   │   │
│   │   └── resources
│   │       ├── application.properties     ← All tunable settings (DHT.K, storage paths, STUN server, etc.)
│   │       ├── static/                    ← Compiled frontend (React/D3) placed under static/dashboard/
│   │       │   └── dashboard/
│   │       │       ├── index.html
│   │       │       ├── main.js
│   │       │       └── styles.css
│   │       └── templates/                 ← (Optional) Thymeleaf / FreeMarker templates if needed
│   │
│   └── test
│       └── java
│           └── com
│               └── example
│                   └── peershare
│                       ├── util/
│                       │   └── PeerIdUtilTest.java
│                       ├── dht/
│                       │   └── RoutingTableTest.java
│                       ├── storage/
│                       │   └── ChunkerTest.java
│                       └── security/
│                           └── AesUtilTest.java
└── target/ 

```

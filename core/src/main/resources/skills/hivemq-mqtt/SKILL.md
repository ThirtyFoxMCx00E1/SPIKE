---
name: hivemq-mqtt
description: HiveMQ broker and MQTT 5 client work (topics, auth, TLS, ACLs)
---
- Topic layout: `spike/<channel>/events` and `spike/<channel>/cmd`. Never put secrets in topics.
- Public brokers are readable by everyone. For private use run HiveMQ Community Edition with a login, TLS (8883) and per-client topic ACLs.
- Client: `com.hivemq:hivemq-mqtt-client`; use MQTT 5, QoS 1 for commands, keep-alive 30 s, unique client ids.
- Broker needs Java 21. Open only the MQTT (1883/8883) and WebSocket ports you use; keep the admin/control center off the internet.

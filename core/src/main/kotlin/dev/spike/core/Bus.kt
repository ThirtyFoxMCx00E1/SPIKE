package dev.spike.core

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.future.await
import java.util.UUID

/**
 * HiveMQ / MQTT 5 bus. Topics: spike/<channel>/events (agent output), spike/<channel>/cmd (send a prompt).
 * Lets the Windows app watch or drive the agent running on your phone, and the reverse.
 * Use your own HiveMQ broker (see ThirtyFoxMCx00E1/HiveMQ) with a login for anything private.
 */
class SpikeBus {
    private var client: Mqtt5AsyncClient? = null
    private var channel = ""

    suspend fun apply(s: Settings, onCommand: (String) -> Unit) {
        client?.disconnect(); client = null
        if (!s.busEnabled) return
        require(s.busChannel.isNotBlank()) { "set a bus channel first" }
        var b = MqttClient.builder().useMqttVersion5()
            .identifier("spike-" + UUID.randomUUID().toString().take(8))
            .serverHost(s.busHost).serverPort(s.busPort)
        if (s.busTls) b = b.sslWithDefaultConfig()
        val c = b.buildAsync()
        val connect = if (s.busUser.isBlank()) c.connectWith().send()
        else c.connectWith().simpleAuth().username(s.busUser).password(s.busPass.toByteArray()).applySimpleAuth().send()
        connect.await()
        c.subscribeWith().topicFilter("spike/${s.busChannel}/cmd").qos(MqttQos.AT_LEAST_ONCE)
            .callback { p -> onCommand(String(p.payloadAsBytes)) }.send().await()
        channel = s.busChannel
        client = c
    }

    suspend fun publish(sub: String, text: String) {
        val c = client ?: return
        if (!c.state.isConnected) return
        c.publishWith().topic("spike/$channel/$sub").qos(MqttQos.AT_LEAST_ONCE).payload(text.toByteArray()).send().await()
    }
}

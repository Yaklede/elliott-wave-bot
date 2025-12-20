package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class BybitPrivateWebSocketClient(
    private val properties: BybitProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = ReactorNettyWebSocketClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(onMessage: (String) -> Unit): Job = scope.launch {
        connectLoop(onMessage)
    }

    private suspend fun connectLoop(onMessage: (String) -> Unit) {
        while (currentCoroutineContext().isActive) {
            try {
                client.execute(URI.create(properties.wsPrivateUrl)) { session ->
                    val authMessage = buildAuthMessage()
                    val subscribeMessage = buildSubscribeMessage()
                    val pingFlux = Flux.interval(Duration.ofSeconds(20))
                        .map {
                            val payload = """{"op":"ping","req_id":"${UUID.randomUUID()}"}"""
                            session.textMessage(payload)
                        }
                    val outbound = Flux.concat(
                        Mono.just(session.textMessage(authMessage)),
                        Mono.just(session.textMessage(subscribeMessage)),
                        pingFlux,
                    )
                    val inbound = session.receive()
                        .map { it.payloadAsText }
                        .doOnNext { onMessage(it) }
                        .then()
                    session.send(outbound).and(inbound)
                }.awaitSingleOrNull()
            } catch (ex: Exception) {
                log.warn("Bybit private WS error: {}", ex.message)
                delay(5000)
            }
        }
    }

    private fun buildAuthMessage(): String {
        val apiKey = properties.apiKey
        val apiSecret = properties.apiSecret
        val expires = System.currentTimeMillis() + 10_000
        val signature = sign(apiSecret, "GET/realtime$expires")
        val payload = mapOf(
            "op" to "auth",
            "args" to listOf(apiKey, expires, signature),
            "req_id" to UUID.randomUUID().toString(),
        )
        return objectMapper.writeValueAsString(payload)
    }

    private fun buildSubscribeMessage(): String {
        val payload = mapOf(
            "op" to "subscribe",
            "args" to listOf("order", "execution"),
        )
        return objectMapper.writeValueAsString(payload)
    }

    private fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val hash = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return java.util.HexFormat.of().formatHex(hash).lowercase()
    }
}

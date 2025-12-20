package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import java.net.URI
import java.time.Duration
import java.util.UUID
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
class BybitWebSocketClient(
    private val properties: BybitProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = ReactorNettyWebSocketClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startPublicKline(symbol: String, interval: String, onMessage: (String) -> Unit): Job = scope.launch {
        val topic = "kline.${interval}.${symbol.uppercase()}"
        val subscribe = """{"op":"subscribe","args":["$topic"]}"""
        connectLoop(properties.wsPublicUrl, subscribe, onMessage)
    }

    private suspend fun connectLoop(url: String, subscribeMessage: String, onMessage: (String) -> Unit) {
        while (currentCoroutineContext().isActive) {
            try {
                client.execute(URI.create(url)) { session ->
                    val pingFlux = Flux.interval(Duration.ofSeconds(20))
                        .map {
                            val payload = """{"op":"ping","req_id":"${UUID.randomUUID()}"}"""
                            session.textMessage(payload)
                        }
                    val outbound = Flux.concat(Mono.just(session.textMessage(subscribeMessage)), pingFlux)
                    val inbound = session.receive()
                        .map { it.payloadAsText }
                        .doOnNext { onMessage(it) }
                        .then()
                    session.send(outbound).and(inbound)
                }.awaitSingleOrNull()
            } catch (ex: Exception) {
                log.warn("Bybit WS error: {}", ex.message)
                delay(5000)
            }
        }
    }
}

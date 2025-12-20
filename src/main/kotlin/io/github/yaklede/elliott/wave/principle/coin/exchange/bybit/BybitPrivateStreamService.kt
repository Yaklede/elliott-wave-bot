package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BotProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BybitPrivateStreamService(
    private val botProperties: BotProperties,
    private val bybitProperties: BybitProperties,
    private val privateWebSocketClient: BybitPrivateWebSocketClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (botProperties.mode != BotMode.LIVE) return
        if (bybitProperties.apiKey.isBlank() || bybitProperties.apiSecret.isBlank()) {
            log.warn("Private WS not started: missing Bybit credentials")
            return
        }
        privateWebSocketClient.start { message ->
            handleMessage(message)
        }
    }

    private fun handleMessage(raw: String) {
        val node = runCatching { objectMapper.readTree(raw) }.getOrNull() ?: return
        val topic = node.get("topic")?.asText()
        if (topic == "order") {
            log.debug("Bybit order stream event received")
        } else if (topic == "execution") {
            log.debug("Bybit execution stream event received")
        } else if (node.get("op")?.asText() == "auth") {
            val success = node.get("success")?.asBoolean() ?: false
            if (!success) {
                log.warn("Bybit private WS auth failed")
            }
        }
    }
}

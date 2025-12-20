package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class BybitTimeSync(
    private val webClient: WebClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val offsetMs = AtomicLong(0)
    private val lastSyncMs = AtomicLong(0)
    private val ttl = Duration.ofSeconds(60).toMillis()

    suspend fun currentTimestampMs(): Long {
        syncIfStale()
        return System.currentTimeMillis() + offsetMs.get()
    }

    suspend fun syncIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastSyncMs.get() < ttl) return
        sync()
    }

    suspend fun sync() {
        val response = webClient.get()
            .uri("/v5/market/time")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<BybitResponse<BybitTimeResult>>() {})
            .awaitSingle()
        val serverTimeMs = response.time ?: response.result?.timeSecond?.toLong()?.times(1000L)
        if (serverTimeMs != null) {
            offsetMs.set(serverTimeMs - System.currentTimeMillis())
            lastSyncMs.set(System.currentTimeMillis())
        } else {
            log.warn("Bybit server time response missing time fields")
        }
    }
}

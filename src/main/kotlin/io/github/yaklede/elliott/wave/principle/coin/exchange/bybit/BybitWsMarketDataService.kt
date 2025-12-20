package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BotProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleRepository
import java.math.BigDecimal
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BybitWsMarketDataService(
    private val botProperties: BotProperties,
    private val bybitProperties: BybitProperties,
    private val webSocketClient: BybitWebSocketClient,
    private val candleRepository: CandleRepository,
    private val objectMapper: ObjectMapper,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (botProperties.mode == BotMode.BACKTEST) return
        webSocketClient.startPublicKline(bybitProperties.symbol, bybitProperties.interval) { message ->
            handleMessage(message)
        }
    }

    private fun handleMessage(raw: String) {
        val parsed = runCatching { objectMapper.readValue(raw, BybitWsKlineMessage::class.java) }.getOrNull()
        if (parsed == null || parsed.data.isEmpty()) return
        parsed.data.forEach { data ->
            if (data.confirm != true) return@forEach
            val candle = data.toCandle() ?: return@forEach
            candleRepository.append(bybitProperties.symbol, bybitProperties.interval, listOf(candle))
        }
    }

    private fun BybitWsKlineData.toCandle(): Candle? {
        val time = start?.toLongOrNull() ?: return null
        val openPrice = open?.toBigDecimalOrNull() ?: return null
        val highPrice = high?.toBigDecimalOrNull() ?: return null
        val lowPrice = low?.toBigDecimalOrNull() ?: return null
        val closePrice = close?.toBigDecimalOrNull() ?: return null
        val volumeValue = volume?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return Candle(
            timeOpenMs = time,
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = volumeValue,
        )
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (ex: NumberFormatException) {
        null
    }

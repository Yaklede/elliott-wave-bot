package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ZigZagMode
import io.github.yaklede.elliott.wave.principle.coin.config.ZigZagProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZigZagExtractorTest {
    @Test
    fun `extracts swing points from percent threshold`() {
        val properties = ZigZagProperties(
            mode = ZigZagMode.PERCENT,
            percentThreshold = BigDecimal("0.10"),
            atrPeriod = 14,
            atrMultiplier = BigDecimal("2.0"),
        )
        val extractor = ZigZagExtractor(properties)
        val prices = listOf("100", "111", "115", "103", "95", "105", "120")
        val candles = prices.mapIndexed { index, price ->
            candle(index.toLong(), price)
        }

        val swings = extractor.extract(candles)

        assertEquals(4, swings.size)
        assertEquals(SwingType.LOW, swings[0].type)
        assertEquals(BigDecimal("100"), swings[0].price)
        assertEquals(SwingType.HIGH, swings[1].type)
        assertEquals(BigDecimal("115"), swings[1].price)
        assertEquals(SwingType.LOW, swings[2].type)
        assertEquals(BigDecimal("95"), swings[2].price)
        assertEquals(SwingType.HIGH, swings[3].type)
        assertEquals(BigDecimal("120"), swings[3].price)
    }

    private fun candle(time: Long, price: String): Candle {
        val p = BigDecimal(price)
        return Candle(
            timeOpenMs = time,
            open = p,
            high = p,
            low = p,
            close = p,
            volume = BigDecimal.ZERO,
        )
    }
}

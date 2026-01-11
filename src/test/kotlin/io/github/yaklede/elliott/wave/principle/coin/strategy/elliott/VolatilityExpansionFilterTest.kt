package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.VolExpansionProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolatilityExpansionFilterTest {
    @Test
    fun `passes when compression occurred and bandwidth is rising`() {
        val props = VolExpansionProperties(
            enabled = true,
            lookback = 12,
            compressionQuantile = BigDecimal("0.3"),
            requireRising = true,
            recentCompressionBars = 6,
            period = 5,
            stdDevMultiplier = BigDecimal("2.0"),
        )
        val filter = VolatilityExpansionFilter(props)
        val flat = (0 until 10).map { idx -> candle(idx.toLong(), BigDecimal("100")) }
        val rising = listOf(
            candle(10, BigDecimal("100")),
            candle(11, BigDecimal("102")),
            candle(12, BigDecimal("104")),
        )
        val candles = flat + rising
        assertTrue(filter.passes(candles))
    }

    @Test
    fun `fails when no compression in recent window`() {
        val props = VolExpansionProperties(
            enabled = true,
            lookback = 12,
            compressionQuantile = BigDecimal("0.3"),
            requireRising = true,
            recentCompressionBars = 6,
            period = 5,
            stdDevMultiplier = BigDecimal("2.0"),
        )
        val filter = VolatilityExpansionFilter(props)
        val candles = (0 until 25).map { idx ->
            candle(idx.toLong(), BigDecimal(100 + idx))
        }
        assertFalse(filter.passes(candles))
    }

    private fun candle(time: Long, close: BigDecimal): Candle = Candle(
        timeOpenMs = time,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = BigDecimal.ONE,
    )
}

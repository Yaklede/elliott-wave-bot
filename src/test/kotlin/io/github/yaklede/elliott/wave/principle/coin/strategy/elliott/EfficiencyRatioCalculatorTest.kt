package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EfficiencyRatioCalculatorTest {
    @Test
    fun `er is near one for monotonic series`() {
        val candles = (0..20).map { idx ->
            candle(idx.toLong(), BigDecimal(idx + 1))
        }
        val er = EfficiencyRatioCalculator().compute(candles, 20)
        assertTrue(er != null && er >= BigDecimal("0.95"))
    }

    @Test
    fun `er is low for choppy series`() {
        val prices = listOf(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1)
        val candles = prices.mapIndexed { idx, price ->
            candle(idx.toLong(), BigDecimal(price))
        }
        val er = EfficiencyRatioCalculator().compute(candles, 20)
        assertTrue(er != null && er < BigDecimal("0.5"))
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

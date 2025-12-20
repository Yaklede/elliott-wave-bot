package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacktestSanityChecksTest {
    private val sanityChecks = BacktestSanityChecks()

    @Test
    fun `sanity checks pass for aligned ascending candles`() {
        val candles = candles(start = 1_700_000_000_000L, count = 5, intervalMs = 900_000L, align = true)
        val result = sanityChecks.validate(candles, 900_000L)
        assertTrue(result.ok)
    }

    @Test
    fun `sanity checks fail for misordered candles`() {
        val candles = candles(start = 1_700_000_000_000L, count = 5, intervalMs = 900_000L, align = true).toMutableList()
        val tmp = candles[2]
        candles[2] = candles[3]
        candles[3] = tmp
        val result = sanityChecks.validate(candles, 900_000L)
        assertFalse(result.ok)
    }

    @Test
    fun `sanity checks fail for misaligned candles`() {
        val candles = candles(start = 1_700_000_000_123L, count = 5, intervalMs = 900_000L, align = false)
        val result = sanityChecks.validate(candles, 900_000L)
        assertFalse(result.ok)
    }

    private fun candles(start: Long, count: Int, intervalMs: Long, align: Boolean): List<Candle> {
        val aligned = if (align) start / intervalMs * intervalMs else start
        return (0 until count).map { idx ->
            val price = BigDecimal("100")
            Candle(
                timeOpenMs = aligned + idx * intervalMs,
                open = price,
                high = price,
                low = price,
                close = price,
                volume = BigDecimal("10"),
            )
        }
    }
}

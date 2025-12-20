package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TradeMetricsCalculatorTest {
    @Test
    fun `expectancy is computed from wins and losses`() {
        val trades = listOf(
            trade(pnl = BigDecimal("10")),
            trade(pnl = BigDecimal("10")),
            trade(pnl = BigDecimal("10")),
            trade(pnl = BigDecimal("-5")),
        )
        val metrics = TradeMetricsCalculator().computeTradeMetrics(trades)
        assertEquals(0, metrics.winRate.compareTo(BigDecimal("0.75")))
        assertEquals(0, metrics.avgWin.compareTo(BigDecimal("10.0")))
        assertEquals(0, metrics.avgLoss.compareTo(BigDecimal("5.0")))
        assertEquals(0, metrics.expectancy.compareTo(BigDecimal("6.25")))
    }

    private fun trade(pnl: BigDecimal): TradeRecord {
        return TradeRecord(
            entryPrice = BigDecimal("100"),
            exitPrice = BigDecimal("101"),
            qty = BigDecimal("1"),
            pnl = pnl,
            entryTimeMs = 0L,
            exitTimeMs = 60_000L,
        )
    }
}

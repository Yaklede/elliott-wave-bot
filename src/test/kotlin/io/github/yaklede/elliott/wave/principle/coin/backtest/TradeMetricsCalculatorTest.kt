package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
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

    @Test
    fun `net expectancy accounts for fees`() {
        val trades = listOf(
            tradeWithFees(grossPnl = BigDecimal("10"), entryFee = BigDecimal("1"), exitFee = BigDecimal("1")),
            tradeWithFees(grossPnl = BigDecimal("-5"), entryFee = BigDecimal("1"), exitFee = BigDecimal("1")),
        )
        val calc = TradeMetricsCalculator()
        val gross = calc.computeGrossTradeMetrics(trades)
        val net = calc.computeTradeMetrics(trades)
        assertEquals(0, gross.expectancy.compareTo(BigDecimal("2.5")))
        assertEquals(0, net.expectancy.compareTo(BigDecimal("0.5")))
    }

    private fun trade(pnl: BigDecimal): TradeRecord {
        return TradeRecord(
            side = PositionSide.LONG,
            entryPrice = BigDecimal("100"),
            exitPrice = BigDecimal("101"),
            qty = BigDecimal("1"),
            pnl = pnl,
            grossPnl = pnl,
            entryFee = BigDecimal.ZERO,
            exitFee = BigDecimal.ZERO,
            entryTimeMs = 0L,
            exitTimeMs = 60_000L,
        )
    }

    private fun tradeWithFees(grossPnl: BigDecimal, entryFee: BigDecimal, exitFee: BigDecimal): TradeRecord {
        val net = grossPnl.subtract(entryFee).subtract(exitFee)
        return TradeRecord(
            side = PositionSide.LONG,
            entryPrice = BigDecimal("100"),
            exitPrice = BigDecimal("101"),
            qty = BigDecimal("1"),
            pnl = net,
            grossPnl = grossPnl,
            entryFee = entryFee,
            exitFee = exitFee,
            entryTimeMs = 0L,
            exitTimeMs = 60_000L,
        )
    }
}

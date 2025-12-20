package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

class TradeMetricsCalculator {
    fun computeTradeMetrics(trades: List<TradeRecord>): TradeMetrics {
        if (trades.isEmpty()) return TradeMetrics.zero()
        val wins = trades.filter { it.pnl > BigDecimal.ZERO }
        val losses = trades.filter { it.pnl < BigDecimal.ZERO }
        val winRate = BigDecimal(wins.size).divide(BigDecimal(trades.size), 4, RoundingMode.HALF_UP)
        val avgWin = average(wins.map { it.pnl })
        val avgLoss = average(losses.map { it.pnl.abs() })
        val expectancy = winRate.multiply(avgWin)
            .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))
        return TradeMetrics(winRate, avgWin, avgLoss, expectancy)
    }

    fun computeDistribution(trades: List<TradeRecord>): DistributionMetrics {
        if (trades.isEmpty()) return DistributionMetrics.zero()
        val sorted = trades.map { it.pnl }.sorted()
        val median = percentile(sorted, 0.5)
        val p25 = percentile(sorted, 0.25)
        val p75 = percentile(sorted, 0.75)
        val largestWin = trades.maxByOrNull { it.pnl }?.pnl ?: BigDecimal.ZERO
        val largestLoss = trades.minByOrNull { it.pnl }?.pnl ?: BigDecimal.ZERO
        return DistributionMetrics(median, p25, p75, largestWin, largestLoss)
    }

    fun computeHolding(trades: List<TradeRecord>): HoldingMetrics {
        if (trades.isEmpty()) return HoldingMetrics.zero()
        val durations = trades.map { Duration.ofMillis(it.exitTimeMs - it.entryTimeMs).toMinutes() }
        val avgMinutes = durations.average()
        val first = trades.minOf { it.entryTimeMs }
        val last = trades.maxOf { it.exitTimeMs }
        val months = Duration.ofMillis(last - first).toDays().toDouble() / 30.0
        val tradesPerMonth = if (months <= 0.0) trades.size.toDouble() else trades.size / months
        return HoldingMetrics(BigDecimal(tradesPerMonth).setScale(2, RoundingMode.HALF_UP), avgMinutes)
    }

    private fun average(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sum = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        return sum.divide(BigDecimal(values.size), 6, RoundingMode.HALF_UP)
    }

    private fun percentile(values: List<BigDecimal>, p: Double): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val index = (p * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
        return values[index]
    }
}

data class TradeMetrics(
    val winRate: BigDecimal,
    val avgWin: BigDecimal,
    val avgLoss: BigDecimal,
    val expectancy: BigDecimal,
) {
    companion object {
        fun zero() = TradeMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

data class DistributionMetrics(
    val median: BigDecimal,
    val p25: BigDecimal,
    val p75: BigDecimal,
    val largestWin: BigDecimal,
    val largestLoss: BigDecimal,
) {
    companion object {
        fun zero() = DistributionMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

data class HoldingMetrics(
    val tradesPerMonth: BigDecimal,
    val avgMinutes: Double,
) {
    companion object {
        fun zero() = HoldingMetrics(BigDecimal.ZERO, 0.0)
    }
}

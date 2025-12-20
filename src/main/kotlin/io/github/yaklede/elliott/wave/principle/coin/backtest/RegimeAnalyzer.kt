package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketKey
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketer
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeThresholds
import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class RegimeAnalyzer {
    fun analyze(
        trades: List<TradeRecord>,
        weakSlope: BigDecimal,
        strongSlope: BigDecimal,
    ): RegimeAnalysisResult {
        val features = trades.mapNotNull { it.features }
        val atrValues = features.mapNotNull { it.atrPercent }
        val volumeValues = features.mapNotNull { it.relVolume }
        val atrLow = quantile(atrValues, 0.33)
        val atrHigh = quantile(atrValues, 0.66)
        val volLow = quantile(volumeValues, 0.33)
        val volHigh = quantile(volumeValues, 0.66)
        val thresholds = RegimeThresholds(
            atrLow = atrLow,
            atrHigh = atrHigh,
            volumeLow = volLow,
            volumeHigh = volHigh,
        )

        val grouped = trades.filter { it.features != null }.groupBy {
            RegimeBucketer.bucket(it.features!!, thresholds, weakSlope, strongSlope)
        }

        val metrics = grouped.map { (bucket, bucketTrades) ->
            val wins = bucketTrades.count { it.pnl > BigDecimal.ZERO }
            val winRate = if (bucketTrades.isEmpty()) BigDecimal.ZERO else {
                BigDecimal(wins).divide(BigDecimal(bucketTrades.size), 4, RoundingMode.HALF_UP)
            }
            val grossProfit = bucketTrades.filter { it.pnl > BigDecimal.ZERO }
                .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.pnl) }
            val grossLoss = bucketTrades.filter { it.pnl < BigDecimal.ZERO }
                .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.pnl.abs()) }
            val profitFactor = if (grossLoss == BigDecimal.ZERO) BigDecimal.ZERO else {
                grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP)
            }
            val avgWin = average(bucketTrades.filter { it.pnl > BigDecimal.ZERO }.map { it.pnl })
            val avgLoss = average(bucketTrades.filter { it.pnl < BigDecimal.ZERO }.map { it.pnl.abs() })
            val expectancy = winRate.multiply(avgWin)
                .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))

            RegimeBucketMetrics(
                bucket = bucket,
                trades = bucketTrades.size,
                profitFactor = profitFactor,
                expectancy = expectancy,
                winRate = winRate,
            )
        }.sortedByDescending { it.trades }

        return RegimeAnalysisResult(thresholds = thresholds, metrics = metrics)
    }

    private fun quantile(values: List<BigDecimal>, q: Double): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sorted = values.sorted()
        val index = (q * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun average(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sum = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        return sum.divide(BigDecimal(values.size), 6, RoundingMode.HALF_UP)
    }
}

data class RegimeAnalysisResult(
    val thresholds: RegimeThresholds,
    val metrics: List<RegimeBucketMetrics>,
)

data class RegimeBucketMetrics(
    val bucket: RegimeBucketKey,
    val trades: Int,
    val profitFactor: BigDecimal,
    val expectancy: BigDecimal,
    val winRate: BigDecimal,
)

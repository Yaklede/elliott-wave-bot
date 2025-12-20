package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.springframework.stereotype.Component

@Component
class ReportService(
    private val regimeAnalyzer: RegimeAnalyzer,
) {
    private val metricsCalculator = TradeMetricsCalculator()
    fun writeStrategyReport(
        result: BacktestResult,
        trades: List<TradeRecord>,
        decisions: List<DecisionRecord>,
        outputDir: Path,
        weakSlope: BigDecimal,
        strongSlope: BigDecimal,
        minTradesPerBucket: Int,
    ): ReportPaths {
        Files.createDirectories(outputDir)
        val reportPath = outputDir.resolve("strategy-report.md")
        val tradesPath = outputDir.resolve("trades.csv")
        val featuresPath = outputDir.resolve("features.csv")

        val metrics = metricsCalculator.computeTradeMetrics(trades)
        val distribution = metricsCalculator.computeDistribution(trades)
        val holding = metricsCalculator.computeHolding(trades)
        val losingPatterns = topLosingPatterns(trades)
        val rejectCounts = decisions.mapNotNull { it.rejectReason }.groupingBy { it }.eachCount()
        val regime = regimeAnalyzer.analyze(trades, weakSlope, strongSlope)
        val suggestedBlocks = regime.metrics
            .filter { it.trades >= minTradesPerBucket && it.expectancy < BigDecimal.ZERO }
            .map { it.bucket }

        val report = buildString {
            appendLine("# Strategy Report")
            appendLine()
            appendLine("## Summary")
            appendLine("- Trades: ${result.trades}")
            appendLine("- Win rate: ${result.winRate}")
            appendLine("- Profit factor: ${result.profitFactor}")
            appendLine("- Max drawdown: ${result.maxDrawdown}")
            appendLine("- Final equity: ${result.finalEquity}")
            appendLine("- Expectancy per trade: ${metrics.expectancy}")
            appendLine()
            appendLine("## Distribution")
            appendLine("- Avg win: ${metrics.avgWin}")
            appendLine("- Avg loss: ${metrics.avgLoss}")
            appendLine("- Median pnl: ${distribution.median}")
            appendLine("- P25/P75 pnl: ${distribution.p25} / ${distribution.p75}")
            appendLine("- Largest win: ${distribution.largestWin}")
            appendLine("- Largest loss: ${distribution.largestLoss}")
            appendLine()
            appendLine("## Activity")
            appendLine("- Trades per month: ${holding.tradesPerMonth}")
            appendLine("- Avg holding (minutes): ${holding.avgMinutes}")
            appendLine()
            appendLine("## Losing patterns (by entry/exit reason)")
            losingPatterns.forEach { (key, loss) ->
                appendLine("- $key: $loss")
            }
            appendLine()
            appendLine("## Rejected signals (by reason)")
            if (rejectCounts.isEmpty()) {
                appendLine("- None")
            } else {
                rejectCounts.forEach { (reason, count) ->
                    appendLine("- $reason: $count")
                }
            }
            appendLine()
            appendLine("## Regime conditioned metrics")
            appendLine("- Thresholds: atrLow=${regime.thresholds.atrLow}, atrHigh=${regime.thresholds.atrHigh}, volumeLow=${regime.thresholds.volumeLow}, volumeHigh=${regime.thresholds.volumeHigh}")
            appendLine()
            appendLine("| Trend | Vol | Volume | Trades | PF | Expectancy | WinRate |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            regime.metrics.forEach { row ->
                appendLine("| ${row.bucket.trend} | ${row.bucket.vol} | ${row.bucket.volume} | ${row.trades} | ${row.profitFactor} | ${row.expectancy} | ${row.winRate} |")
            }
            appendLine()
            appendLine("## Suggested regime gate (negative expectancy)")
            if (suggestedBlocks.isEmpty()) {
                appendLine("- None (no buckets below expectancy threshold with sufficient trades)")
            } else {
                appendLine("- minTradesPerBucket: $minTradesPerBucket")
                appendLine()
                appendLine("Suggested config:")
                appendLine("```yaml")
                appendLine("strategy:")
                appendLine("  features:")
                appendLine("    enableRegimeGate: true")
                appendLine("  regime:")
                appendLine("    thresholds:")
                appendLine("      atrLow: ${regime.thresholds.atrLow}")
                appendLine("      atrHigh: ${regime.thresholds.atrHigh}")
                appendLine("      volumeLow: ${regime.thresholds.volumeLow}")
                appendLine("      volumeHigh: ${regime.thresholds.volumeHigh}")
                appendLine("    blocked:")
                suggestedBlocks.forEach { bucket ->
                    appendLine("      - ${bucket.trend}|${bucket.vol}|${bucket.volume}")
                }
                appendLine("```")
            }
        }

        Files.writeString(reportPath, report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        writeTradesCsv(tradesPath, trades)
        writeFeaturesCsv(featuresPath, trades)

        return ReportPaths(reportPath, tradesPath, featuresPath)
    }

    private fun topLosingPatterns(trades: List<TradeRecord>): List<Pair<String, BigDecimal>> {
        val losses = trades.filter { it.pnl < BigDecimal.ZERO }
        val grouped = losses.groupBy { "${it.entryReason ?: "UNKNOWN"} -> ${it.exitReason ?: "UNKNOWN"}" }
        return grouped.mapValues { (_, list) ->
            list.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.pnl) }
        }.toList().sortedBy { it.second }.take(10)
    }

    private fun writeTradesCsv(path: Path, trades: List<TradeRecord>) {
        val header = listOf(
            "entryTimeMs", "exitTimeMs", "entryPrice", "exitPrice", "qty", "pnl",
            "entryReason", "exitReason", "score", "confidence",
            "trendSlope", "maSpread", "atrPercent", "relVolume",
        ).joinToString(",")
        val lines = trades.map { trade ->
            val f = trade.features
            listOf(
                trade.entryTimeMs,
                trade.exitTimeMs,
                trade.entryPrice,
                trade.exitPrice,
                trade.qty,
                trade.pnl,
                trade.entryReason,
                trade.exitReason,
                trade.entryScore,
                trade.confidenceScore,
                f?.trendSlope,
                f?.maSpread,
                f?.atrPercent,
                f?.relVolume,
            ).joinToString(",")
        }
        writeCsv(path, header, lines)
    }

    private fun writeFeaturesCsv(path: Path, trades: List<TradeRecord>) {
        val header = listOf("entryTimeMs", "trendSlope", "maSpread", "atrPercent", "relVolume").joinToString(",")
        val lines = trades.mapNotNull { trade ->
            val f = trade.features ?: return@mapNotNull null
            listOf(
                trade.entryTimeMs,
                f.trendSlope,
                f.maSpread,
                f.atrPercent,
                f.relVolume,
            ).joinToString(",")
        }
        writeCsv(path, header, lines)
    }

    private fun writeCsv(path: Path, header: String, lines: List<String>) {
        Files.writeString(path, header + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        if (lines.isNotEmpty()) {
            Files.write(path, lines.map { it + "\n" }, StandardOpenOption.APPEND)
        }
    }

}

data class ReportPaths(
    val reportPath: Path,
    val tradesCsv: Path,
    val featuresCsv: Path,
)

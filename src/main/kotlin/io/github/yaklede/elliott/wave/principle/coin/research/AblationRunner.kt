package io.github.yaklede.elliott.wave.principle.coin.research

import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestSimulator
import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.ExitModel
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class AblationRunner(
    private val simulator: BacktestSimulator,
    private val baseStrategy: StrategyProperties,
    private val riskProperties: RiskProperties,
    private val backtestProperties: BacktestProperties,
) {
    fun run(candles: List<Candle>, outputDir: Path): List<AblationResult> {
        Files.createDirectories(outputDir)
        val cases = listOf(
            AblationCase("baseline", baseStrategy),
            AblationCase(
                "no_wave_filter",
                baseStrategy.copy(features = baseStrategy.features.copy(enableWaveFilter = false))
            ),
            AblationCase(
                "no_volume_filter",
                baseStrategy.copy(features = baseStrategy.features.copy(enableVolumeFilter = false))
            ),
            AblationCase(
                "no_trend_filter",
                baseStrategy.copy(features = baseStrategy.features.copy(enableTrendFilter = false))
            ),
            AblationCase(
                "atr_exit",
                baseStrategy.copy(features = baseStrategy.features.copy(exitModel = ExitModel.ATR_DYNAMIC))
            ),
        )

        val results = cases.map { case ->
            val engine = StrategyEngine(case.strategy)
            val riskManager = RiskManager(riskProperties)
            val portfolio = PortfolioService(backtestProperties)
            val run = simulator.run(
                candles = candles,
                strategyEngine = engine,
                riskManager = riskManager,
                portfolioService = portfolio,
                recordDecisions = false,
            )
            val expectancy = computeExpectancy(run.trades)
            AblationResult(case.name, run.result, expectancy, run.trades.size)
        }

        val report = buildString {
            appendLine("# Ablation Report")
            appendLine()
            appendLine("| Case | Trades | PF | Expectancy | MaxDD | FinalEquity |")
            appendLine("| --- | --- | --- | --- | --- | --- |")
            results.forEach { r ->
                appendLine("| ${r.name} | ${r.trades} | ${r.result.profitFactor} | ${r.expectancy} | ${r.result.maxDrawdown} | ${r.result.finalEquity} |")
            }
        }
        Files.writeString(
            outputDir.resolve("ablation-report.md"),
            report,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        return results
    }

    private fun computeExpectancy(trades: List<io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord>): BigDecimal {
        if (trades.isEmpty()) return BigDecimal.ZERO
        val wins = trades.filter { it.pnl > BigDecimal.ZERO }
        val losses = trades.filter { it.pnl < BigDecimal.ZERO }
        val winRate = BigDecimal(wins.size).divide(BigDecimal(trades.size), 4, RoundingMode.HALF_UP)
        val avgWin = average(wins.map { it.pnl })
        val avgLoss = average(losses.map { it.pnl.abs() })
        return winRate.multiply(avgWin)
            .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))
    }

    private fun average(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sum = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        return sum.divide(BigDecimal(values.size), 6, RoundingMode.HALF_UP)
    }
}

data class AblationCase(
    val name: String,
    val strategy: StrategyProperties,
)

data class AblationResult(
    val name: String,
    val result: io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestResult,
    val expectancy: BigDecimal,
    val trades: Int,
)

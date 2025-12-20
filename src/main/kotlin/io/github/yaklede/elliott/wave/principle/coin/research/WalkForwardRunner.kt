package io.github.yaklede.elliott.wave.principle.coin.research

import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestSimulator
import io.github.yaklede.elliott.wave.principle.coin.backtest.RegimeAnalyzer
import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.ExitModel
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.config.WalkForwardProperties
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeGate
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.random.Random

class WalkForwardRunner(
    private val simulator: BacktestSimulator,
    private val baseStrategy: StrategyProperties,
    private val riskProperties: RiskProperties,
    private val backtestProperties: BacktestProperties,
    private val regimeAnalyzer: RegimeAnalyzer,
) {
    fun run(candles: List<Candle>, outputDir: Path, props: WalkForwardProperties, interval: String): WalkForwardReport {
        Files.createDirectories(outputDir)
        val intervalMs = IntervalUtil.intervalToMillis(interval)
        val barsPerDay = (24 * 60 * 60 * 1000L / intervalMs).toInt().coerceAtLeast(1)
        val trainBars = props.trainDays * barsPerDay
        val testBars = props.testDays * barsPerDay

        val folds = mutableListOf<WalkForwardFoldResult>()
        var startIndex = 0
        while (startIndex + trainBars + testBars <= candles.size) {
            val trainSlice = candles.subList(startIndex, startIndex + trainBars)
            val testSlice = candles.subList(startIndex + trainBars, startIndex + trainBars + testBars)

            val candidates = generateCandidates(baseStrategy, props.maxTrials)
            var best: CandidateResult? = null
            candidates.forEach { candidate ->
                val result = runCandidate(candidate, trainSlice)
                if (best == null || result.score > best!!.score) {
                    best = result
                }
            }
            val bestCandidate = best ?: break
            val gate = if (props.enableRegimeGate) {
                val regime = regimeAnalyzer.analyze(bestCandidate.trades, baseStrategy.regime.weakSlope, baseStrategy.regime.strongSlope)
                val blocked = regime.metrics
                    .filter { it.trades >= baseStrategy.regime.minTradesPerBucket && it.expectancy < BigDecimal.ZERO }
                    .map { it.bucket }
                    .toSet()
                RegimeGate(regime.thresholds, blocked, baseStrategy.regime.minTradesPerBucket)
            } else {
                null
            }

            val testResult = runCandidate(bestCandidate.strategy, testSlice, gate)

            folds.add(
                WalkForwardFoldResult(
                    trainTrades = bestCandidate.trades.size,
                    testTrades = testResult.trades.size,
                    trainProfitFactor = bestCandidate.result.profitFactor,
                    testProfitFactor = testResult.result.profitFactor,
                    trainExpectancy = bestCandidate.expectancy,
                    testExpectancy = testResult.expectancy,
                    testMaxDrawdown = testResult.result.maxDrawdown,
                    strategy = bestCandidate.strategy,
                )
            )

            startIndex += testBars
        }

        val report = buildString {
            appendLine("# Walk-Forward Report")
            appendLine()
            appendLine("| Fold | TrainTrades | TestTrades | TrainPF | TestPF | TrainExp | TestExp | TestMaxDD |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
            folds.forEachIndexed { index, fold ->
                appendLine(
                    "| ${index + 1} | ${fold.trainTrades} | ${fold.testTrades} | ${fold.trainProfitFactor} | ${fold.testProfitFactor} | ${fold.trainExpectancy} | ${fold.testExpectancy} | ${fold.testMaxDrawdown} |"
                )
            }
        }
        Files.writeString(
            outputDir.resolve("walk-forward-report.md"),
            report,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        return WalkForwardReport(folds)
    }

    private fun runCandidate(strategy: StrategyProperties, candles: List<Candle>, gate: RegimeGate? = null): CandidateResult {
        val engine = StrategyEngine(strategy)
        val riskManager = RiskManager(riskProperties)
        val portfolio = PortfolioService(backtestProperties)
        val run = simulator.run(
            candles = candles,
            strategyEngine = engine,
            riskManager = riskManager,
            portfolioService = portfolio,
            regimeGate = gate,
            recordDecisions = false,
        )
        val expectancy = computeExpectancy(run.trades)
        val score = expectancy.toDouble() - run.result.maxDrawdown.toDouble() * 0.5
        return CandidateResult(strategy, run.result, run.trades, expectancy, score)
    }

    private fun generateCandidates(base: StrategyProperties, maxTrials: Int): List<StrategyProperties> {
        val zigzagOptions = listOf(BigDecimal("0.01"), BigDecimal("0.015"), BigDecimal("0.02"))
        val scoreOptions = listOf(BigDecimal("0.55"), BigDecimal("0.60"), BigDecimal("0.65"))
        val stopOptions = listOf(BigDecimal("1.2"), BigDecimal("1.5"), BigDecimal("2.0"))
        val tpOptions = listOf(BigDecimal("2.0"), BigDecimal("3.0"))
        val timeOptions = listOf(24, 48)
        val combos = mutableListOf<StrategyProperties>()
        for (zz in zigzagOptions) {
            for (score in scoreOptions) {
                for (stop in stopOptions) {
                    for (tp in tpOptions) {
                        for (time in timeOptions) {
                            combos.add(
                                base.copy(
                                    zigzag = base.zigzag.copy(percentThreshold = zz),
                                    elliott = base.elliott.copy(minScoreToTrade = score),
                                    exit = base.exit.copy(
                                        atrStopMultiplier = stop,
                                        atrTakeProfitMultiplier = tp,
                                        timeStopBars = time,
                                    ),
                                    features = base.features.copy(exitModel = ExitModel.HYBRID),
                                )
                            )
                        }
                    }
                }
            }
        }
        val rng = Random(42)
        return combos.shuffled(rng).take(maxTrials)
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

data class WalkForwardReport(
    val folds: List<WalkForwardFoldResult>,
)

data class WalkForwardFoldResult(
    val trainTrades: Int,
    val testTrades: Int,
    val trainProfitFactor: BigDecimal,
    val testProfitFactor: BigDecimal,
    val trainExpectancy: BigDecimal,
    val testExpectancy: BigDecimal,
    val testMaxDrawdown: BigDecimal,
    val strategy: StrategyProperties,
)

private data class CandidateResult(
    val strategy: StrategyProperties,
    val result: io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestResult,
    val trades: List<io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord>,
    val expectancy: BigDecimal,
    val score: Double,
)

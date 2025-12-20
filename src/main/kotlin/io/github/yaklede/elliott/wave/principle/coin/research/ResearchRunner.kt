package io.github.yaklede.elliott.wave.principle.coin.research

import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestSimulator
import io.github.yaklede.elliott.wave.principle.coin.backtest.CsvCandleLoader
import io.github.yaklede.elliott.wave.principle.coin.backtest.ReportService
import io.github.yaklede.elliott.wave.principle.coin.backtest.RegimeAnalyzer
import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestSanityChecks
import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.ResearchMode
import io.github.yaklede.elliott.wave.principle.coin.config.ResearchProperties
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ResearchRunner(
    private val researchProperties: ResearchProperties,
    private val backtestProperties: BacktestProperties,
    private val bybitProperties: BybitProperties,
    private val riskProperties: RiskProperties,
    private val strategyProperties: StrategyProperties,
    private val bybitV5Client: BybitV5Client,
    private val candleResampler: CandleResampler,
    private val orderPriceService: OrderPriceService,
    private val sanityChecks: BacktestSanityChecks,
    private val reportService: ReportService,
    private val regimeAnalyzer: RegimeAnalyzer,
    private val context: ConfigurableApplicationContext,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val csvLoader = CsvCandleLoader()

    @EventListener(ApplicationReadyEvent::class)
    fun runIfEnabled() {
        if (!researchProperties.enabled) return

        val candles = loadCandles()
        if (candles.isEmpty()) {
            log.warn("Research run skipped: no candles available")
            context.close()
            return
        }

        val simulator = BacktestSimulator(
            properties = backtestProperties,
            bybitProperties = bybitProperties,
            candleResampler = candleResampler,
            orderPriceService = orderPriceService,
            sanityChecks = sanityChecks,
        )
        val outputDir = Path.of(researchProperties.outputDir)

        when (researchProperties.mode) {
            ResearchMode.REPORT -> {
                val engine = StrategyEngine(strategyProperties)
                val riskManager = RiskManager(riskProperties)
                val portfolio = PortfolioService(backtestProperties)
                val run = simulator.run(
                    candles = candles,
                    strategyEngine = engine,
                    riskManager = riskManager,
                    portfolioService = portfolio,
                    recordDecisions = true,
                )
                reportService.writeStrategyReport(
                    result = run.result,
                    trades = run.trades,
                    decisions = run.decisions,
                    outputDir = outputDir,
                    weakSlope = strategyProperties.regime.weakSlope,
                    strongSlope = strategyProperties.regime.strongSlope,
                )
            }
            ResearchMode.ABLATION -> {
                val runner = AblationRunner(simulator, strategyProperties, riskProperties, backtestProperties)
                runner.run(candles, outputDir)
            }
            ResearchMode.WALK_FORWARD -> {
                val runner = WalkForwardRunner(
                    simulator = simulator,
                    baseStrategy = strategyProperties,
                    riskProperties = riskProperties,
                    backtestProperties = backtestProperties,
                    regimeAnalyzer = regimeAnalyzer,
                )
                runner.run(candles, outputDir, researchProperties.walkForward, bybitProperties.interval)
            }
        }

        log.info("Research run completed in mode {}", researchProperties.mode)
        context.close()
    }

    private fun loadCandles(): List<Candle> {
        val envPath = System.getenv("BACKTEST_DATA_PATH")
        val csvPath = envPath ?: backtestProperties.csvPath
        if (!csvPath.isNullOrBlank()) {
            val candles = csvLoader.load(csvPath)
            if (candles.isNotEmpty()) return candles
        }
        val samplePath = "data/sample_btcusdt_15m.csv"
        if (File(samplePath).exists()) {
            val candles = csvLoader.load(samplePath)
            if (candles.isNotEmpty()) return candles
        }
        val start = backtestProperties.startMs
        val end = backtestProperties.endMs
        if (start != null && end != null) {
            return runBlocking {
                bybitV5Client.getKlinesPaged(
                    category = bybitProperties.category,
                    symbol = bybitProperties.symbol,
                    interval = bybitProperties.interval,
                    start = start,
                    end = end,
                )
            }
        }
        return emptyList()
    }
}

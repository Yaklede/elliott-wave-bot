package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.execution.RegimeGateProvider
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BacktestRunner(
    private val properties: BacktestProperties,
    private val bybitProperties: BybitProperties,
    private val bybitV5Client: BybitV5Client,
    private val candleResampler: CandleResampler,
    private val orderPriceService: OrderPriceService,
    private val strategyEngine: StrategyEngine,
    private val strategyProperties: StrategyProperties,
    private val riskManager: RiskManager,
    private val portfolioService: PortfolioService,
    private val botStateStore: BotStateStore,
    private val sanityChecks: BacktestSanityChecks,
    private val reportService: ReportService,
    private val regimeGateProvider: RegimeGateProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val csvLoader = CsvCandleLoader()
    private val lastReport = AtomicReference<BacktestReport?>(null)
    private val simulator = BacktestSimulator(
        properties = properties,
        bybitProperties = bybitProperties,
        candleResampler = candleResampler,
        orderPriceService = orderPriceService,
        sanityChecks = sanityChecks,
    )

    suspend fun runIfConfigured() {
        val candles = loadCandles(null)
        if (candles.isEmpty()) {
            log.info("Backtest skipped: no candles available")
            return
        }
        val report = runReport(candles)
        log.info(
            "Backtest complete: trades={}, winRate={}, profitFactor={}, maxDrawdown={}, finalEquity={}",
            report.result.trades,
            report.result.winRate,
            report.result.profitFactor,
            report.result.maxDrawdown,
            report.result.finalEquity,
        )
    }

    fun run(candles: List<Candle>): BacktestResult {
        val gate = regimeGateProvider.currentGate()
        return simulator.run(
            candles = candles,
            strategyEngine = strategyEngine,
            riskManager = riskManager,
            portfolioService = portfolioService,
            botStateStore = botStateStore,
            regimeGate = gate,
            recordDecisions = false,
        ).result
    }

    fun runReport(candles: List<Candle>): BacktestReport {
        val gate = regimeGateProvider.currentGate()
        val run = simulator.run(
            candles = candles,
            strategyEngine = strategyEngine,
            riskManager = riskManager,
            portfolioService = portfolioService,
            botStateStore = botStateStore,
            regimeGate = gate,
            recordDecisions = true,
        )
        reportService.writeStrategyReport(
            result = run.result,
            trades = run.trades,
            decisions = run.decisions,
            outputDir = Path.of("build/reports"),
            weakSlope = strategyProperties.regime.weakSlope,
            strongSlope = strategyProperties.regime.strongSlope,
            minTradesPerBucket = strategyProperties.regime.minTradesPerBucket,
        )
        val report = BacktestReport(run.result, run.trades)
        lastReport.set(report)
        return report
    }

    suspend fun runForRequest(request: BacktestRequest): BacktestReport {
        val candles = loadCandles(request)
        if (candles.isEmpty()) {
            val empty = BacktestReport(
                result = BacktestResult(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, properties.initialCapital),
                trades = emptyList(),
            )
            lastReport.set(empty)
            return empty
        }
        return runReport(candles)
    }

    fun lastReport(): BacktestReport? = lastReport.get()

    private suspend fun loadCandles(request: BacktestRequest?): List<Candle> {
        val csvPath = request?.csvPath ?: properties.csvPath
        if (!csvPath.isNullOrBlank()) {
            val candles = csvLoader.load(csvPath)
            if (candles.isNotEmpty()) return candles
            log.info("Backtest CSV empty: {}", csvPath)
        }
        val samplePath = "data/sample_btcusdt_15m.csv"
        if (File(samplePath).exists()) {
            val candles = csvLoader.load(samplePath)
            if (candles.isNotEmpty()) return candles
        }
        val start = request?.startMs ?: properties.startMs
        val end = request?.endMs ?: properties.endMs
        if (start != null && end != null) {
            return bybitV5Client.getKlinesPaged(
                category = bybitProperties.category,
                symbol = bybitProperties.symbol,
                interval = bybitProperties.interval,
                start = start,
                end = end,
            )
        }
        return emptyList()
    }

}

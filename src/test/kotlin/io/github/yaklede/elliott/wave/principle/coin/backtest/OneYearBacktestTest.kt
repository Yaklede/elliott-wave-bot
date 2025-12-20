package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.execution.RegimeGateProvider
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentInfoService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import io.mockk.mockk
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("backtest")
class OneYearBacktestTest {
    @Test
    fun `runs one-year backtest from local csv`() {
        val csvPath = System.getenv("BACKTEST_DATA_PATH")?.takeIf { it.isNotBlank() }
            ?: "data/bybit_btcusdt_15m_1y.csv"
        assumeTrue(File(csvPath).exists(), "Missing backtest CSV: $csvPath")

        val backtestProperties = BacktestProperties(
            initialCapital = BigDecimal("1000"),
            feeRate = BigDecimal("0.0006"),
            slippageBps = 2,
            startMs = null,
            endMs = null,
            csvPath = csvPath,
        )
        val bybitProperties = BybitProperties(
            interval = "15",
            htfInterval = "60",
        )
        val strategyProperties = StrategyProperties()
        val strategyEngine = StrategyEngine(strategyProperties)
        val riskManager = RiskManager(RiskProperties())
        val portfolioService = PortfolioService(backtestProperties)
        val botStateStore = BotStateStore()
        val bybitClient = mockk<BybitV5Client>(relaxed = true)
        val instrumentInfoService = mockk<InstrumentInfoService>(relaxed = true)
        val resampler = CandleResampler()
        val orderPriceService = OrderPriceService(instrumentInfoService, bybitProperties)
        val sanityChecks = BacktestSanityChecks()
        val reportService = ReportService(RegimeAnalyzer())
        val regimeGateProvider = RegimeGateProvider(strategyProperties)

        val runner = BacktestRunner(
            properties = backtestProperties,
            bybitProperties = bybitProperties,
            bybitV5Client = bybitClient,
            candleResampler = resampler,
            orderPriceService = orderPriceService,
            strategyEngine = strategyEngine,
            strategyProperties = strategyProperties,
            riskManager = riskManager,
            portfolioService = portfolioService,
            botStateStore = botStateStore,
            sanityChecks = sanityChecks,
            reportService = reportService,
            regimeGateProvider = regimeGateProvider,
        )

        val candles = CsvCandleLoader().load(csvPath)
        val report = runner.runReport(candles)

        val summary = buildString {
            append("One-year backtest result: ")
            append("trades=").append(report.result.trades).append(", ")
            append("winRate=").append(report.result.winRate).append(", ")
            append("profitFactor=").append(report.result.profitFactor).append(", ")
            append("maxDrawdown=").append(report.result.maxDrawdown).append(", ")
            append("finalEquity=").append(report.result.finalEquity)
        }
        println(summary)
        writeSummary(summary)

        assertTrue(report.result.trades > 0)
        assertTrue(report.result.finalEquity > BigDecimal.ZERO)
    }

    private fun writeSummary(summary: String) {
        val dir = Path.of("build", "reports", "backtest")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("one-year-result.txt"), summary)
    }
}

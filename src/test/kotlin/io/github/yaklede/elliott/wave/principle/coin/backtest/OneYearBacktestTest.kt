package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentInfoService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import io.mockk.mockk
import java.io.File
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag

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
        val strategyEngine = StrategyEngine(StrategyProperties())
        val riskManager = RiskManager(RiskProperties())
        val portfolioService = PortfolioService(backtestProperties)
        val botStateStore = BotStateStore()
        val bybitClient = mockk<BybitV5Client>(relaxed = true)
        val instrumentInfoService = mockk<InstrumentInfoService>(relaxed = true)
        val resampler = CandleResampler()
        val orderPriceService = OrderPriceService(instrumentInfoService, bybitProperties)

        val runner = BacktestRunner(
            properties = backtestProperties,
            bybitProperties = bybitProperties,
            bybitV5Client = bybitClient,
            candleResampler = resampler,
            orderPriceService = orderPriceService,
            strategyEngine = strategyEngine,
            riskManager = riskManager,
            portfolioService = portfolioService,
            botStateStore = botStateStore,
        )

        val candles = CsvCandleLoader().load(csvPath)
        val report = runner.runReport(candles)

        println(
            "One-year backtest result: trades=${report.result.trades}, winRate=${report.result.winRate}, " +
                "profitFactor=${report.result.profitFactor}, maxDrawdown=${report.result.maxDrawdown}, " +
                "finalEquity=${report.result.finalEquity}"
        )

        assertTrue(report.result.trades > 0)
        assertTrue(report.result.finalEquity > BigDecimal.ZERO)
    }
}

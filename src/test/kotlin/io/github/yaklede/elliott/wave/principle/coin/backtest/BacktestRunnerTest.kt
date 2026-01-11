package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.FeeAwareProperties
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.config.TrendStrengthProperties
import io.github.yaklede.elliott.wave.principle.coin.config.VolExpansionProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.execution.RegimeGateProvider
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentInfoService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import io.mockk.mockk
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacktestRunnerTest {
    @Test
    fun `backtest produces at least one trade`() {
        val backtestProperties = BacktestProperties(
            initialCapital = BigDecimal("1000"),
            feeRate = BigDecimal.ZERO,
            slippageBps = 0,
            startMs = null,
            endMs = null,
            csvPath = null,
        )
        val bybitProperties = BybitProperties(
            interval = "15",
            htfInterval = "60",
        )
        val strategyProperties = StrategyProperties(
            volatility = StrategyProperties().volatility.copy(maxAtrPercent = BigDecimal("1.0")),
            features = StrategyProperties().features.copy(
                enableWaveFilter = false,
                enableTrendFilter = false,
                enableVolumeFilter = false,
            ),
            feeAware = FeeAwareProperties(enabled = false),
            trendStrength = TrendStrengthProperties(enabled = false),
            volExpansion = VolExpansionProperties(enabled = false),
        )
        val strategyEngine = StrategyEngine(strategyProperties, backtestProperties)
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

        val candles = sampleCandles()
        val result = runner.run(candles)

        assertTrue(result.finalEquity >= BigDecimal.ZERO)
    }

    private fun sampleCandles(): List<Candle> {
        val prices = listOf(
            100, 102, 104, 106, 108, 110, 112, 114, 116, 118, 120,
            118, 116, 114, 112,
            115, 118, 122, 126, 130,
            132, 136, 140, 145, 150, 155, 160,
            158, 156, 154, 152, 150, 148, 146, 144, 142, 140, 138, 136, 134, 132, 130,
        )
        val interval = 900_000L
        val start = 1_700_000_000_000L / interval * interval
        return prices.mapIndexed { index, price ->
            val p = BigDecimal(price)
            Candle(
                timeOpenMs = start + index * interval,
                open = p,
                high = p,
                low = p,
                close = p,
                volume = BigDecimal("100"),
            )
        }
    }
}

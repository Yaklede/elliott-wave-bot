package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.SignalType
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
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
    private val riskManager: RiskManager,
    private val portfolioService: PortfolioService,
    private val botStateStore: BotStateStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val csvLoader = CsvCandleLoader()
    private val lastReport = AtomicReference<BacktestReport?>(null)

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
        portfolioService.reset(properties.initialCapital)
        val startTime = Instant.ofEpochMilli(candles.first().timeOpenMs)
        riskManager.resetForBacktest(properties.initialCapital, startTime)

        var equityPeak = properties.initialCapital
        var maxDrawdown = BigDecimal.ZERO
        val htfIntervalMs = IntervalUtil.intervalToMillis(bybitProperties.htfInterval)
        val htfCandles = candleResampler.resample(candles, htfIntervalMs)
        var htfIndex = 0

        for (i in candles.indices) {
            val window = candles.subList(0, i + 1)
            val candle = candles[i]
            val now = Instant.ofEpochMilli(candle.timeOpenMs)
            while (htfIndex < htfCandles.size && htfCandles[htfIndex].timeOpenMs <= candle.timeOpenMs) {
                htfIndex += 1
            }
            val htfWindow = if (htfIndex == 0) emptyList() else htfCandles.subList(0, htfIndex)

            portfolioService.markToMarket(candle.close)
            riskManager.updateEquity(portfolioService.equity.add(portfolioService.unrealizedPnl()), now)

            val signal = strategyEngine.evaluate(window, htfWindow)
            if (portfolioService.position.side == PositionSide.FLAT && signal.type == SignalType.ENTER_LONG) {
                if (riskManager.canEnter(now)) {
                    val stop = signal.stopPrice ?: continue
                    val entry = signal.entryPrice ?: candle.close
                    val takeProfit = signal.takeProfitPrice ?: entry
                    val priceLevels = orderPriceService.adjustPrices(entry, stop, takeProfit, null) ?: continue
                    val qty = riskManager.computeOrderQty(portfolioService.equity, priceLevels.entry, priceLevels.stop)
                    if (qty > BigDecimal.ZERO) {
                        val fillPrice = applySlippage(priceLevels.entry, properties.slippageBps, isBuy = true)
                        portfolioService.enterLong(
                            qty = qty,
                            price = fillPrice,
                            stopPrice = priceLevels.stop,
                            takeProfitPrice = priceLevels.takeProfit,
                            feeRate = properties.feeRate,
                            timeMs = candle.timeOpenMs,
                        )
                    }
                }
            }

            val position = portfolioService.position
            if (position.side == PositionSide.LONG) {
                val stop = position.stopPrice
                val takeProfit = position.takeProfitPrice
                if (stop != null && candle.close <= stop) {
                    val exitPrice = applySlippage(stop, properties.slippageBps, isBuy = false)
                    val pnl = portfolioService.exitLong(exitPrice, properties.feeRate, candle.timeOpenMs)
                    riskManager.recordTradeResult(pnl, now)
                } else if (takeProfit != null && candle.close >= takeProfit) {
                    val exitPrice = applySlippage(takeProfit, properties.slippageBps, isBuy = false)
                    val pnl = portfolioService.exitLong(exitPrice, properties.feeRate, candle.timeOpenMs)
                    riskManager.recordTradeResult(pnl, now)
                }
            }

            val equity = portfolioService.equity.add(portfolioService.unrealizedPnl())
            if (equity > equityPeak) equityPeak = equity
            val drawdown = equityPeak.subtract(equity)
                .divide(equityPeak, 6, RoundingMode.HALF_UP)
            if (drawdown > maxDrawdown) maxDrawdown = drawdown

            botStateStore.update(
                mode = io.github.yaklede.elliott.wave.principle.coin.config.BotMode.BACKTEST,
                symbol = "BACKTEST",
                lastCandleTime = candle.timeOpenMs,
                position = portfolioService.position,
                lastSignal = signal.type.name,
                killSwitchActive = riskManager.isKillSwitchActive(),
            )
        }

        val trades = portfolioService.tradeHistory()
        val wins = trades.count { it.pnl > BigDecimal.ZERO }
        val winRate = if (trades.isEmpty()) BigDecimal.ZERO else {
            BigDecimal(wins).divide(BigDecimal(trades.size), 4, RoundingMode.HALF_UP)
        }
        val grossProfit = trades.filter { it.pnl > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.pnl) }
        val grossLoss = trades.filter { it.pnl < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.pnl.abs()) }
        val profitFactor = if (grossLoss == BigDecimal.ZERO) BigDecimal.ZERO else {
            grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP)
        }

        return BacktestResult(
            trades = trades.size,
            winRate = winRate,
            profitFactor = profitFactor,
            maxDrawdown = maxDrawdown,
            finalEquity = portfolioService.equity,
        )
    }

    fun runReport(candles: List<Candle>): BacktestReport {
        val result = run(candles)
        val report = BacktestReport(result, portfolioService.tradeHistory())
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

    private fun applySlippage(price: BigDecimal, bps: Int, isBuy: Boolean): BigDecimal {
        if (bps <= 0) return price
        val multiplier = BigDecimal(bps).divide(BigDecimal(10_000))
        val adjustment = price.multiply(multiplier)
        return if (isBuy) price.add(adjustment) else price.subtract(adjustment)
    }
}

package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BotProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.ResearchProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.marketdata.MarketDataService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioStore
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskStateStore
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.SignalType
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import io.github.yaklede.elliott.wave.principle.coin.domain.ExitReason
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent

@Component
class ExecutionEngine(
    private val botProperties: BotProperties,
    private val bybitProperties: BybitProperties,
    private val backtestProperties: BacktestProperties,
    private val researchProperties: ResearchProperties,
    private val marketDataService: MarketDataService,
    private val strategyEngine: StrategyEngine,
    private val riskManager: RiskManager,
    private val portfolioService: PortfolioService,
    private val portfolioStore: PortfolioStore,
    private val orderSizingService: OrderSizingService,
    private val orderPriceService: OrderPriceService,
    private val bybitV5Client: BybitV5Client,
    private val liveModeGuard: LiveModeGuard,
    private val botStateStore: BotStateStore,
    private val riskStateStore: RiskStateStore,
    private val regimeGateProvider: RegimeGateProvider,
    private val backtestRunner: io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastCandleTime: Long? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (researchProperties.enabled) {
            log.info("Research mode enabled; execution engine is idle")
            return
        }
        when (botProperties.mode) {
            BotMode.BACKTEST -> scope.launch { backtestRunner.runIfConfigured() }
            BotMode.PAPER -> startPaper(live = false)
            BotMode.LIVE -> startPaper(live = true)
        }
    }

    private fun startPaper(live: Boolean) {
        scope.launch {
            portfolioStore.load()?.let { snapshot ->
                portfolioService.restore(snapshot)
            }
            riskStateStore.load()?.let { snapshot ->
                riskManager.restore(snapshot)
            }
            val pollDelayMs = 15_000L
            while (isActive) {
                try {
                    val candles = marketDataService.refreshRecent(
                        bybitProperties.category,
                        bybitProperties.symbol,
                        bybitProperties.interval,
                        300,
                    )
                    val htfCandles = marketDataService.refreshRecent(
                        bybitProperties.category,
                        bybitProperties.symbol,
                        bybitProperties.htfInterval,
                        300,
                    )
                    if (candles.isNotEmpty()) {
                        val last = candles.last()
                        if (lastCandleTime != last.timeOpenMs) {
                            lastCandleTime = last.timeOpenMs
                            processCandle(last.timeOpenMs, last.close, candles, htfCandles, live)
                        }
                    }
                } catch (ex: Exception) {
                    log.warn("Paper loop error: {}", ex.message)
                }
                delay(pollDelayMs)
            }
        }
    }

    private suspend fun processCandle(
        candleTimeMs: Long,
        closePrice: BigDecimal,
        candles: List<io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle>,
        htfCandles: List<io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle>,
        live: Boolean,
    ) {
        val intervalMs = IntervalUtil.intervalToMillis(bybitProperties.interval)
        val now = Instant.ofEpochMilli(candleTimeMs)
        portfolioService.markToMarket(closePrice)
        riskManager.updateEquity(portfolioService.equity.add(portfolioService.unrealizedPnl()), now)
        riskStateStore.save(riskManager.snapshot())

        val gate = regimeGateProvider.currentGate()
        val signal = strategyEngine.evaluate(candles, htfCandles, gate)
        val position = portfolioService.position

        if (position.side == PositionSide.FLAT && signal.type == SignalType.ENTER_LONG) {
            if (riskManager.canEnter(now)) {
                val plan = signal.exitPlan ?: return
                val stop = plan.stopPrice ?: return
                val entry = signal.entryPrice ?: closePrice
                val takeProfit = plan.takeProfitPrice ?: entry
                val priceLevels = orderPriceService.adjustPrices(entry, stop, takeProfit) ?: return
                val qty = orderSizingService.computeQty(portfolioService.equity, priceLevels.entry, priceLevels.stop)
                if (qty > BigDecimal.ZERO) {
                    if (live) {
                        liveModeGuard.ensureLiveAllowed()
                        val orderLinkId = UUID.randomUUID().toString()
                        bybitV5Client.placeOrderSpotMarket(
                            symbol = bybitProperties.symbol,
                            side = "Buy",
                            qty = qty,
                            orderLinkId = orderLinkId,
                        )
                    }
                    val fillPrice = applySlippage(priceLevels.entry, backtestProperties.slippageBps, isBuy = true)
                    portfolioService.enterLong(
                        qty = qty,
                        price = fillPrice,
                        stopPrice = priceLevels.stop,
                        takeProfitPrice = priceLevels.takeProfit,
                        trailActivationPrice = plan.trailActivationPrice,
                        trailDistance = plan.trailDistance,
                        timeStopBars = plan.timeStopBars,
                        breakEvenPrice = plan.breakEvenPrice,
                        feeRate = backtestProperties.feeRate,
                        timeMs = candleTimeMs,
                        entryReason = signal.entryReason,
                        entryScore = signal.score,
                        confidenceScore = signal.confidence,
                        features = signal.features,
                    )
                    portfolioStore.save(portfolioService.snapshot())
                    riskStateStore.save(riskManager.snapshot())
                }
            }
        }

        val openPosition = portfolioService.position
        if (openPosition.side == PositionSide.LONG) {
            val stop = openPosition.stopPrice
            val takeProfit = openPosition.takeProfitPrice
            val exitReason = when {
                stop != null && closePrice <= stop -> if (openPosition.trailingActive) ExitReason.TRAIL_STOP else ExitReason.STOP_INVALIDATION
                takeProfit != null && closePrice >= takeProfit -> ExitReason.TAKE_PROFIT
                else -> null
            }
            if (exitReason != null) {
                val exitPrice = if (exitReason == ExitReason.TAKE_PROFIT) takeProfit!! else stop!!
                val fill = applySlippage(exitPrice, backtestProperties.slippageBps, isBuy = false)
                val pnl = portfolioService.exitLong(fill, backtestProperties.feeRate, candleTimeMs, exitReason)
                riskManager.recordTradeResult(pnl, now)
                portfolioStore.save(portfolioService.snapshot())
                riskStateStore.save(riskManager.snapshot())
            } else {
                val breakEven = openPosition.breakEvenPrice
                if (breakEven != null && closePrice >= breakEven) {
                    val currentStop = openPosition.stopPrice
                    if (currentStop != null && currentStop < openPosition.avgPrice) {
                        portfolioService.updateStopLoss(openPosition.avgPrice, openPosition.trailingActive)
                    }
                }
                val updated = updateTrailingStop(openPosition, closePrice)
                if (updated != null) portfolioService.updateStopLoss(updated.first, updated.second)
                if (openPosition.timeStopBars != null && openPosition.entryTimeMs != null) {
                    val barsHeld = ((candleTimeMs - openPosition.entryTimeMs) / intervalMs) + 1
                    if (barsHeld >= openPosition.timeStopBars) {
                        val fill = applySlippage(closePrice, backtestProperties.slippageBps, isBuy = false)
                        val pnl = portfolioService.exitLong(fill, backtestProperties.feeRate, candleTimeMs, ExitReason.TIME_STOP)
                        riskManager.recordTradeResult(pnl, now)
                        portfolioStore.save(portfolioService.snapshot())
                        riskStateStore.save(riskManager.snapshot())
                    }
                }
            }
        }

        botStateStore.update(
            mode = botProperties.mode,
            symbol = bybitProperties.symbol,
            lastCandleTime = candleTimeMs,
            position = portfolioService.position,
            lastSignal = signal.type.name,
            killSwitchActive = riskManager.isKillSwitchActive(),
        )
    }

    private fun applySlippage(price: BigDecimal, bps: Int, isBuy: Boolean): BigDecimal {
        if (bps <= 0) return price
        val multiplier = BigDecimal(bps).divide(BigDecimal(10_000))
        val adjustment = price.multiply(multiplier)
        return if (isBuy) price.add(adjustment) else price.subtract(adjustment)
    }

    private fun updateTrailingStop(
        position: io.github.yaklede.elliott.wave.principle.coin.portfolio.Position,
        closePrice: BigDecimal,
    ): Pair<BigDecimal, Boolean>? {
        val activation = position.trailActivationPrice ?: return null
        val distance = position.trailDistance ?: return null
        if (closePrice < activation) return null
        val candidate = closePrice.subtract(distance)
        val current = position.stopPrice ?: return null
        if (candidate > current) return candidate to true
        return null
    }
}

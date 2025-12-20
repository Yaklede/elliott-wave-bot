package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BotProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.marketdata.MarketDataService
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioStore
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskStateStore
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.SignalType
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
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
    private val backtestRunner: io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastCandleTime: Long? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
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
        val now = Instant.ofEpochMilli(candleTimeMs)
        portfolioService.markToMarket(closePrice)
        riskManager.updateEquity(portfolioService.equity.add(portfolioService.unrealizedPnl()), now)
        riskStateStore.save(riskManager.snapshot())

        val signal = strategyEngine.evaluate(candles, htfCandles)
        val position = portfolioService.position

        if (position.side == PositionSide.FLAT && signal.type == SignalType.ENTER_LONG) {
            if (riskManager.canEnter(now)) {
                val stop = signal.stopPrice ?: return
                val entry = signal.entryPrice ?: closePrice
                val takeProfit = signal.takeProfitPrice ?: entry
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
                        feeRate = backtestProperties.feeRate,
                        timeMs = candleTimeMs,
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
            if (stop != null && closePrice <= stop) {
                val exitPrice = applySlippage(stop, backtestProperties.slippageBps, isBuy = false)
                val pnl = portfolioService.exitLong(exitPrice, backtestProperties.feeRate, candleTimeMs)
                riskManager.recordTradeResult(pnl, now)
                portfolioStore.save(portfolioService.snapshot())
                riskStateStore.save(riskManager.snapshot())
            } else if (takeProfit != null && closePrice >= takeProfit) {
                val exitPrice = applySlippage(takeProfit, backtestProperties.slippageBps, isBuy = false)
                val pnl = portfolioService.exitLong(exitPrice, backtestProperties.feeRate, candleTimeMs)
                riskManager.recordTradeResult(pnl, now)
                portfolioStore.save(portfolioService.snapshot())
                riskStateStore.save(riskManager.snapshot())
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
}

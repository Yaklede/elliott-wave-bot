package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import io.github.yaklede.elliott.wave.principle.coin.execution.OrderPriceService
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.CandleResampler
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PortfolioService
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.SignalType
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.StrategyEngine
import io.github.yaklede.elliott.wave.principle.coin.domain.ExitReason
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeGate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class BacktestSimulator(
    private val properties: BacktestProperties,
    private val bybitProperties: BybitProperties,
    private val strategyProperties: StrategyProperties,
    private val candleResampler: CandleResampler,
    private val orderPriceService: OrderPriceService,
    private val sanityChecks: BacktestSanityChecks,
) {
    fun run(
        candles: List<Candle>,
        strategyEngine: StrategyEngine,
        riskManager: RiskManager,
        portfolioService: PortfolioService,
        botStateStore: BotStateStore? = null,
        regimeGate: RegimeGate? = null,
        recordDecisions: Boolean = false,
    ): BacktestRunResult {
        val intervalMs = IntervalUtil.intervalToMillis(bybitProperties.interval)
        sanityChecks.validateOrThrow(candles, intervalMs)

        portfolioService.reset(properties.initialCapital)
        val startTime = Instant.ofEpochMilli(candles.first().timeOpenMs)
        riskManager.resetForBacktest(properties.initialCapital, startTime)

        var equityPeak = properties.initialCapital
        var maxDrawdown = BigDecimal.ZERO
        val htfIntervalMs = IntervalUtil.intervalToMillis(bybitProperties.htfInterval)
        val htfCandles = candleResampler.resample(candles, htfIntervalMs)
        var htfIndex = 0
        val maxLookbackBars = properties.maxLookbackBars
        val maxHtfLookbackBars = properties.maxHtfLookbackBars ?: maxLookbackBars

        var pendingSignal: io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.TradeSignal? = null
        val decisions = mutableListOf<DecisionRecord>()

        for (i in candles.indices) {
            val candle = candles[i]
            val now = Instant.ofEpochMilli(candle.timeOpenMs)
            while (htfIndex < htfCandles.size && htfCandles[htfIndex].timeOpenMs <= candle.timeOpenMs) {
                htfIndex += 1
            }
            val htfStart = if (htfIndex > maxHtfLookbackBars) htfIndex - maxHtfLookbackBars else 0
            val htfWindow = if (htfIndex == 0) emptyList() else htfCandles.subList(htfStart, htfIndex)

            if (pendingSignal != null) {
                val plan = pendingSignal!!.exitPlan
                val isLongSignal = pendingSignal!!.type == SignalType.ENTER_LONG
                val isShortSignal = pendingSignal!!.type == SignalType.ENTER_SHORT
                val position = portfolioService.position
                if ((isLongSignal || isShortSignal) && plan?.stopPrice != null) {
                    if (!riskManager.canEnter(now)) {
                        if (recordDecisions) {
                            decisions.add(
                                DecisionRecord(
                                    timeMs = candle.timeOpenMs,
                                    signalType = SignalType.HOLD,
                                    entryReason = pendingSignal!!.entryReason,
                                    rejectReason = riskManager.entryBlockReason(now),
                                    score = pendingSignal!!.score,
                                    confidence = pendingSignal!!.confidence,
                                    features = pendingSignal!!.features,
                                )
                            )
                        }
                        pendingSignal = null
                        continue
                    }
                    val entry = candle.open
                    if (position.side == PositionSide.FLAT) {
                        val takeProfit = plan.takeProfitPrice ?: entry
                        val priceLevels = orderPriceService.adjustPrices(entry, plan.stopPrice, takeProfit, null, isLongSignal)
                        if (priceLevels != null) {
                            val qty = riskManager.computeOrderQty(portfolioService.equity, priceLevels.entry, priceLevels.stop)
                            if (qty > BigDecimal.ZERO) {
                                val fillPrice = applySlippage(priceLevels.entry, properties.slippageBps, isBuy = isLongSignal)
                                if (isLongSignal) {
                                    portfolioService.enterLong(
                                        qty = qty,
                                        price = fillPrice,
                                        stopPrice = priceLevels.stop,
                                        takeProfitPrice = priceLevels.takeProfit,
                                        trailActivationPrice = plan.trailActivationPrice,
                                        trailDistance = plan.trailDistance,
                                        timeStopBars = plan.timeStopBars,
                                        breakEvenPrice = plan.breakEvenPrice,
                                        feeRate = properties.feeRate,
                                        timeMs = candle.timeOpenMs,
                                        entryReason = pendingSignal!!.entryReason,
                                        entryScore = pendingSignal!!.score,
                                        confidenceScore = pendingSignal!!.confidence,
                                        features = pendingSignal!!.features,
                                    )
                                } else {
                                    portfolioService.enterShort(
                                        qty = qty,
                                        price = fillPrice,
                                        stopPrice = priceLevels.stop,
                                        takeProfitPrice = priceLevels.takeProfit,
                                        trailActivationPrice = plan.trailActivationPrice,
                                        trailDistance = plan.trailDistance,
                                        timeStopBars = plan.timeStopBars,
                                        breakEvenPrice = plan.breakEvenPrice,
                                        feeRate = properties.feeRate,
                                        timeMs = candle.timeOpenMs,
                                        entryReason = pendingSignal!!.entryReason,
                                        entryScore = pendingSignal!!.score,
                                        confidenceScore = pendingSignal!!.confidence,
                                        features = pendingSignal!!.features,
                                    )
                                }
                            }
                        }
                    } else if (canAdd(position, candle.timeOpenMs, intervalMs, isLongSignal, isShortSignal)) {
                        val stop = position.stopPrice
                        val takeProfit = position.takeProfitPrice
                        if (stop != null && takeProfit != null) {
                            val priceLevels = orderPriceService.adjustPrices(entry, stop, takeProfit, null, isLongSignal)
                            if (priceLevels != null) {
                                val qty = riskManager.computeOrderQty(
                                    equity = portfolioService.equity,
                                    entryPrice = priceLevels.entry,
                                    stopPrice = priceLevels.stop,
                                    riskFraction = strategyProperties.pyramiding.addOnRiskFraction,
                                )
                                if (qty > BigDecimal.ZERO) {
                                    val fillPrice = applySlippage(priceLevels.entry, properties.slippageBps, isBuy = isLongSignal)
                                    portfolioService.addToPosition(qty, fillPrice, properties.feeRate, candle.timeOpenMs)
                                }
                            }
                        }
                    }
                    pendingSignal = null
                }
            }

            portfolioService.markToMarket(candle.close)
            riskManager.updateEquity(portfolioService.equity.add(portfolioService.unrealizedPnl()), now)

            val position = portfolioService.position
            if (position.side == PositionSide.LONG || position.side == PositionSide.SHORT) {
                val stop = position.stopPrice
                val takeProfit = position.takeProfitPrice
                val exitReason = when (position.side) {
                    PositionSide.LONG -> when {
                        stop != null && candle.low <= stop -> if (position.trailingActive) ExitReason.TRAIL_STOP else ExitReason.STOP_INVALIDATION
                        takeProfit != null && candle.high >= takeProfit -> ExitReason.TAKE_PROFIT
                        else -> null
                    }
                    PositionSide.SHORT -> when {
                        stop != null && candle.high >= stop -> if (position.trailingActive) ExitReason.TRAIL_STOP else ExitReason.STOP_INVALIDATION
                        takeProfit != null && candle.low <= takeProfit -> ExitReason.TAKE_PROFIT
                        else -> null
                    }
                    else -> null
                }
                if (exitReason != null) {
                    val exitPrice = if (exitReason == ExitReason.TAKE_PROFIT) takeProfit!! else stop!!
                    val fill = applySlippage(exitPrice, properties.slippageBps, isBuy = position.side == PositionSide.SHORT)
                    val pnl = if (position.side == PositionSide.LONG) {
                        portfolioService.exitLong(fill, properties.feeRate, candle.timeOpenMs, exitReason)
                    } else {
                        portfolioService.exitShort(fill, properties.feeRate, candle.timeOpenMs, exitReason)
                    }
                    riskManager.recordTradeResult(pnl, now)
                } else {
                    val breakEven = position.breakEvenPrice
                    if (breakEven != null) {
                        val shouldMove = when (position.side) {
                            PositionSide.LONG -> candle.high >= breakEven
                            PositionSide.SHORT -> candle.low <= breakEven
                            else -> false
                        }
                        if (shouldMove) {
                            val currentStop = position.stopPrice
                            if (currentStop != null) {
                                val shouldUpdate = when (position.side) {
                                    PositionSide.LONG -> currentStop < position.avgPrice
                                    PositionSide.SHORT -> currentStop > position.avgPrice
                                    else -> false
                                }
                                if (shouldUpdate) {
                                    portfolioService.updateStopLoss(position.avgPrice, position.trailingActive)
                                }
                            }
                        }
                    }
                    val updated = updateTrailingStop(position, candle.close)
                    if (updated != null) {
                        portfolioService.updateStopLoss(updated.first, updated.second)
                    }
                    if (position.timeStopBars != null && position.entryTimeMs != null) {
                        val barsHeld = ((candle.timeOpenMs - position.entryTimeMs) / intervalMs) + 1
                        if (barsHeld >= position.timeStopBars) {
                            val fill = applySlippage(candle.close, properties.slippageBps, isBuy = position.side == PositionSide.SHORT)
                            val pnl = if (position.side == PositionSide.LONG) {
                                portfolioService.exitLong(fill, properties.feeRate, candle.timeOpenMs, ExitReason.TIME_STOP)
                            } else {
                                portfolioService.exitShort(fill, properties.feeRate, candle.timeOpenMs, ExitReason.TIME_STOP)
                            }
                            riskManager.recordTradeResult(pnl, now)
                        }
                    }
                }
            }

            val equity = portfolioService.equity.add(portfolioService.unrealizedPnl())
            if (equity > equityPeak) equityPeak = equity
            val drawdown = equityPeak.subtract(equity)
                .divide(equityPeak, 6, RoundingMode.HALF_UP)
            if (drawdown > maxDrawdown) maxDrawdown = drawdown

            val windowStart = if (i + 1 > maxLookbackBars) (i + 1 - maxLookbackBars) else 0
            val window = candles.subList(windowStart, i + 1)
            val signal = strategyEngine.evaluate(window, htfWindow, regimeGate)
            if (recordDecisions) {
                decisions.add(
                    DecisionRecord(
                        timeMs = candle.timeOpenMs,
                        signalType = signal.type,
                        entryReason = signal.entryReason,
                        rejectReason = signal.rejectReason,
                        score = signal.score,
                        confidence = signal.confidence,
                        features = signal.features,
                    )
                )
            }
            pendingSignal = if (signal.type == SignalType.ENTER_LONG || signal.type == SignalType.ENTER_SHORT) signal else null

            botStateStore?.update(
                mode = BotMode.BACKTEST,
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

        val result = BacktestResult(
            trades = trades.size,
            winRate = winRate,
            profitFactor = profitFactor,
            maxDrawdown = maxDrawdown,
            finalEquity = portfolioService.equity,
        )

        return BacktestRunResult(
            result = result,
            trades = trades,
            decisions = decisions,
        )
    }

    private fun canAdd(
        position: io.github.yaklede.elliott.wave.principle.coin.portfolio.Position,
        timeMs: Long,
        intervalMs: Long,
        isLongSignal: Boolean,
        isShortSignal: Boolean,
    ): Boolean {
        val pyramiding = strategyProperties.pyramiding
        if (!pyramiding.enabled) return false
        if (position.addsCount >= pyramiding.maxAdds) return false
        if (position.side == PositionSide.LONG && !isLongSignal) return false
        if (position.side == PositionSide.SHORT && !isShortSignal) return false
        val lastAdd = position.lastAddTimeMs ?: position.entryTimeMs ?: return true
        val bars = (timeMs - lastAdd) / intervalMs
        return bars >= pyramiding.minBarsBetweenAdds
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
        val current = position.stopPrice ?: return null
        return when (position.side) {
            PositionSide.LONG -> {
                if (closePrice < activation) return null
                val candidate = closePrice.subtract(distance)
                if (candidate > current) candidate to true else null
            }
            PositionSide.SHORT -> {
                if (closePrice > activation) return null
                val candidate = closePrice.add(distance)
                if (candidate < current) candidate to true else null
            }
            else -> null
        }
    }
}

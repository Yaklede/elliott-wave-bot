package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ExitModel
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import java.math.BigDecimal

class ExitPlanBuilder(
    private val properties: StrategyProperties,
) {
    fun build(
        entryPrice: BigDecimal,
        stopCandidate: BigDecimal,
        takeProfitCandidate: BigDecimal,
        atrValue: BigDecimal?,
        isLong: Boolean,
    ): ExitPlan {
        val exitModel = properties.features.exitModel
        val atrStop = atrValue?.multiply(properties.exit.atrStopMultiplier)
        val atrTakeProfit = atrValue?.multiply(properties.exit.atrTakeProfitMultiplier)
        val atrTrailActivation = atrValue?.multiply(properties.exit.trailActivationAtr)
        val atrTrailDistance = atrValue?.multiply(properties.exit.trailDistanceAtr)
        val atrBreakEven = atrValue?.multiply(properties.exit.breakEvenAtr)

        val stop = when (exitModel) {
            ExitModel.FIXED, ExitModel.TIME_STOP -> stopCandidate
            ExitModel.ATR_DYNAMIC -> if (atrStop != null) {
                if (isLong) entryPrice.subtract(atrStop) else entryPrice.add(atrStop)
            } else stopCandidate
            ExitModel.HYBRID -> {
                val atrStopPrice = if (atrStop != null) {
                    if (isLong) entryPrice.subtract(atrStop) else entryPrice.add(atrStop)
                } else stopCandidate
                if (isLong) maxOf(stopCandidate, atrStopPrice) else minOf(stopCandidate, atrStopPrice)
            }
        }

        val takeProfit = when (exitModel) {
            ExitModel.FIXED, ExitModel.TIME_STOP -> takeProfitCandidate
            ExitModel.ATR_DYNAMIC -> if (atrTakeProfit != null) {
                if (isLong) entryPrice.add(atrTakeProfit) else entryPrice.subtract(atrTakeProfit)
            } else takeProfitCandidate
            ExitModel.HYBRID -> {
                val atrTpPrice = if (atrTakeProfit != null) {
                    if (isLong) entryPrice.add(atrTakeProfit) else entryPrice.subtract(atrTakeProfit)
                } else takeProfitCandidate
                if (isLong) maxOf(takeProfitCandidate, atrTpPrice) else minOf(takeProfitCandidate, atrTpPrice)
            }
        }

        val trailActivationPrice = if (atrTrailActivation != null) {
            if (isLong) entryPrice.add(atrTrailActivation) else entryPrice.subtract(atrTrailActivation)
        } else null
        val trailDistance = atrTrailDistance
        val breakEvenPrice = if (atrBreakEven != null && properties.exit.breakEvenAtr > BigDecimal.ZERO) {
            if (isLong) entryPrice.add(atrBreakEven) else entryPrice.subtract(atrBreakEven)
        } else {
            null
        }
        val timeStopBars = when (exitModel) {
            ExitModel.TIME_STOP, ExitModel.HYBRID -> properties.exit.timeStopBars
            else -> null
        }

        return ExitPlan(
            stopPrice = stop,
            takeProfitPrice = takeProfit,
            trailActivationPrice = trailActivationPrice,
            trailDistance = trailDistance,
            timeStopBars = timeStopBars,
            breakEvenPrice = breakEvenPrice,
        )
    }
}

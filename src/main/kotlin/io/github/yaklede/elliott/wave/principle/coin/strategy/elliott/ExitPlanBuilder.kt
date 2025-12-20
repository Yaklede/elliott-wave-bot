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
    ): ExitPlan {
        val exitModel = properties.features.exitModel
        val atrStop = atrValue?.multiply(properties.exit.atrStopMultiplier)
        val atrTakeProfit = atrValue?.multiply(properties.exit.atrTakeProfitMultiplier)
        val atrTrailActivation = atrValue?.multiply(properties.exit.trailActivationAtr)
        val atrTrailDistance = atrValue?.multiply(properties.exit.trailDistanceAtr)

        val stop = when (exitModel) {
            ExitModel.FIXED, ExitModel.TIME_STOP -> stopCandidate
            ExitModel.ATR_DYNAMIC -> if (atrStop != null) entryPrice.subtract(atrStop) else stopCandidate
            ExitModel.HYBRID -> {
                val atrStopPrice = if (atrStop != null) entryPrice.subtract(atrStop) else stopCandidate
                maxOf(stopCandidate, atrStopPrice)
            }
        }

        val takeProfit = when (exitModel) {
            ExitModel.FIXED, ExitModel.TIME_STOP -> takeProfitCandidate
            ExitModel.ATR_DYNAMIC -> if (atrTakeProfit != null) entryPrice.add(atrTakeProfit) else takeProfitCandidate
            ExitModel.HYBRID -> {
                val atrTpPrice = if (atrTakeProfit != null) entryPrice.add(atrTakeProfit) else takeProfitCandidate
                maxOf(takeProfitCandidate, atrTpPrice)
            }
        }

        val trailActivationPrice = if (atrTrailActivation != null) entryPrice.add(atrTrailActivation) else null
        val trailDistance = atrTrailDistance
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
        )
    }
}

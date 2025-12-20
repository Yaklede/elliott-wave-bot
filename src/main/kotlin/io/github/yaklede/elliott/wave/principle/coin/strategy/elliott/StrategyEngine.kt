package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.EntryModel
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketer
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeGate
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
import io.github.yaklede.elliott.wave.principle.coin.domain.RejectReason
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class StrategyEngine(
    private val properties: StrategyProperties,
) {
    private val zigZagExtractor = ZigZagExtractor(properties.zigzag)
    private val detector = ElliottWaveDetector()
    private val scorer = ElliottScorer()
    private val atrCalculator = ATRCalculator()
    private val featureCalculator = RegimeFeatureCalculator(atrCalculator)
    private val exitPlanBuilder = ExitPlanBuilder(properties)

    fun evaluate(
        candles: List<Candle>,
        htfCandles: List<Candle>,
        regimeGate: RegimeGate? = null,
    ): TradeSignal {
        if (candles.size < 10) return hold(RejectReason.NO_SETUP)

        val features = featureCalculator.calculate(
            candles = candles,
            htfCandles = htfCandles,
            volumePeriod = properties.volume.period,
            atrPeriod = properties.volatility.atrPeriod,
            slopeLookbackBars = properties.regime.slopeLookbackBars,
        )

        if (properties.features.enableTrendFilter && !passesTrendFilter(htfCandles)) {
            return hold(RejectReason.TREND_FILTER, features)
        }
        if (!passesVolatilityFilter(candles)) {
            return hold(RejectReason.VOLATILITY_FILTER, features)
        }
        if (properties.features.enableVolumeFilter && !passesVolumeFilter(candles)) {
            return hold(RejectReason.VOLUME_FILTER, features)
        }
        if (properties.features.enableRegimeGate && regimeGate != null && features != null) {
            val bucket = RegimeBucketer.bucket(
                features = features,
                thresholds = regimeGate.thresholds,
                weakSlope = properties.regime.weakSlope,
                strongSlope = properties.regime.strongSlope,
            )
            if (regimeGate.isBlocked(bucket)) {
                return hold(RejectReason.REGIME_GATED, features)
            }
        }

        return if (properties.features.enableWaveFilter) {
            evaluateWave(candles, htfCandles, features)
        } else {
            evaluateSwingBreak(candles, features)
        }
    }

    private fun evaluateWave(
        candles: List<Candle>,
        htfCandles: List<Candle>,
        features: RegimeFeatures?,
    ): TradeSignal {
        val swings = zigZagExtractor.extract(candles)
        val wave2 = detector.findWave2Setup(swings) ?: return hold(RejectReason.NO_SETUP, features)

        val atrValue = atrCalculator.calculate(candles, properties.volatility.atrPeriod)
            .lastOrNull { it != null }
        val score = scorer.scoreWave2(wave2, candles, htfCandles, properties.elliott, properties.volume)
        val confidence = scorer.confidenceScore(
            setup = wave2,
            candles = candles,
            htfCandles = htfCandles,
            elliott = properties.elliott,
            volume = properties.volume,
            atrValue = atrValue,
        )

        val threshold = properties.elliott.minScoreToTrade
        val lastClose = candles.last().close
        val entryOk = when (properties.features.entryModel) {
            EntryModel.BASELINE -> score >= threshold
            EntryModel.CONFIDENCE_THRESHOLD -> confidence >= threshold
            EntryModel.MOMENTUM_CONFIRM -> score >= threshold && lastClose > candles[candles.size - 2].high
        }
        if (!entryOk) return hold(RejectReason.LOW_SCORE, features, score, confidence)

        if (lastClose > wave2.wave1End.price) {
            val wave1Size = wave2.wave1End.price.subtract(wave2.wave1Start.price)
            val fixedTakeProfit = wave2.wave1End.price.add(
                wave1Size.multiply(properties.elliott.fib.takeProfitExtension)
            )
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = wave2.wave1Start.price,
                takeProfitCandidate = fixedTakeProfit,
                atrValue = atrValue,
            )
            if (isStopTooWide(lastClose, exitPlan.stopPrice, atrValue)) {
                return hold(RejectReason.STOP_DISTANCE, features, score, confidence)
            }
            if (isRewardRiskTooLow(lastClose, exitPlan)) {
                return hold(RejectReason.LOW_REWARD_RISK, features, score, confidence)
            }
            return TradeSignal(
                type = SignalType.ENTER_LONG,
                entryPrice = lastClose,
                exitPlan = exitPlan,
                score = score,
                confidence = confidence,
                entryReason = EntryReason.W2_COMPLETE_BREAK_W1_END,
                features = features,
            )
        }
        return hold(RejectReason.NO_SETUP, features, score, confidence)
    }

    private fun evaluateSwingBreak(candles: List<Candle>, features: RegimeFeatures?): TradeSignal {
        val swings = zigZagExtractor.extract(candles)
        val lastHigh = swings.lastOrNull { it.type == SwingType.HIGH }
        val lastLow = swings.lastOrNull { it.type == SwingType.LOW }
        if (lastHigh == null || lastLow == null) return hold(RejectReason.NO_SETUP, features)

        val lastClose = candles.last().close
        val prevHigh = candles[candles.size - 2].high
        val entryOk = when (properties.features.entryModel) {
            EntryModel.MOMENTUM_CONFIRM -> lastClose > prevHigh
            else -> true
        }
        if (!entryOk) return hold(RejectReason.LOW_SCORE, features)

        if (lastClose > lastHigh.price) {
            val stop = lastLow.price
            val takeProfit = lastClose.add(
                lastClose.subtract(stop).multiply(properties.elliott.fib.takeProfitExtension)
            )
            val atrValue = atrCalculator.calculate(candles, properties.volatility.atrPeriod)
                .lastOrNull { it != null }
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = stop,
                takeProfitCandidate = takeProfit,
                atrValue = atrValue,
            )
            if (isStopTooWide(lastClose, exitPlan.stopPrice, atrValue)) {
                return hold(RejectReason.STOP_DISTANCE, features)
            }
            if (isRewardRiskTooLow(lastClose, exitPlan)) {
                return hold(RejectReason.LOW_REWARD_RISK, features)
            }
            return TradeSignal(
                type = SignalType.ENTER_LONG,
                entryPrice = lastClose,
                exitPlan = exitPlan,
                score = null,
                confidence = null,
                entryReason = EntryReason.SWING_BREAKOUT,
                features = features,
            )
        }
        return hold(RejectReason.NO_SETUP, features)
    }

    private fun hold(
        reason: RejectReason?,
        features: RegimeFeatures? = null,
        score: BigDecimal? = null,
        confidence: BigDecimal? = null,
    ): TradeSignal = TradeSignal(
        type = SignalType.HOLD,
        score = score,
        confidence = confidence,
        rejectReason = reason,
        features = features,
    )

    private fun passesTrendFilter(htfCandles: List<Candle>): Boolean {
        if (htfCandles.size < 200) return false
        val sma50 = sma(htfCandles.takeLast(50))
        val sma200 = sma(htfCandles.takeLast(200))
        return sma50 > sma200
    }

    private fun passesVolatilityFilter(candles: List<Candle>): Boolean {
        val period = properties.volatility.atrPeriod
        if (candles.size < period + 1) return true
        val atrValues = atrCalculator.calculate(candles, period)
        val atr = atrValues.lastOrNull { it != null } ?: return true
        val close = candles.last().close
        if (close <= BigDecimal.ZERO) return true
        val atrPercent = atr.divide(close, 6, RoundingMode.HALF_UP)
        val min = properties.volatility.minAtrPercent
        if (min > BigDecimal.ZERO && atrPercent < min) return false
        return atrPercent <= properties.volatility.maxAtrPercent
    }

    private fun passesVolumeFilter(candles: List<Candle>): Boolean {
        val period = properties.volume.period
        if (candles.size < period + 1) return true
        val recent = candles.takeLast(period)
        val avg = recent.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.volume) }
            .divide(BigDecimal(period), 6, RoundingMode.HALF_UP)
        if (avg <= BigDecimal.ZERO) return true
        val lastVolume = candles.last().volume
        return lastVolume >= avg.multiply(properties.volume.minMultiplier)
    }

    private fun sma(candles: List<Candle>): BigDecimal {
        val sum = candles.fold(BigDecimal.ZERO) { acc, candle -> acc.add(candle.close) }
        return sum.divide(BigDecimal(candles.size), 8, RoundingMode.HALF_UP)
    }

    private fun isStopTooWide(
        entryPrice: BigDecimal,
        stopPrice: BigDecimal?,
        atrValue: BigDecimal?,
    ): Boolean {
        if (stopPrice == null || atrValue == null) return false
        if (properties.exit.maxStopAtrMultiplier <= BigDecimal.ZERO) return false
        val maxDistance = atrValue.multiply(properties.exit.maxStopAtrMultiplier)
        val distance = entryPrice.subtract(stopPrice).abs()
        return distance > maxDistance
    }

    private fun isRewardRiskTooLow(entryPrice: BigDecimal, plan: ExitPlan?): Boolean {
        if (plan == null) return false
        val stop = plan.stopPrice ?: return false
        val takeProfit = plan.takeProfitPrice ?: return false
        val risk = entryPrice.subtract(stop).abs()
        val reward = takeProfit.subtract(entryPrice).abs()
        if (risk <= BigDecimal.ZERO) return true
        val rr = reward.divide(risk, 6, RoundingMode.HALF_UP)
        return rr < properties.entry.minRewardRisk
    }
}

package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import io.github.yaklede.elliott.wave.principle.coin.config.EntryModel
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.config.TrendStrengthModel
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
    private val backtestProperties: BacktestProperties,
) {
    private val zigZagExtractor = ZigZagExtractor(properties.zigzag)
    private val detector = ElliottWaveDetector()
    private val scorer = ElliottScorer()
    private val atrCalculator = ATRCalculator()
    private val featureCalculator = RegimeFeatureCalculator(atrCalculator)
    private val exitPlanBuilder = ExitPlanBuilder(properties)
    private val feeAwareGate = FeeAwareGate(properties.feeAware, backtestProperties.feeRate, backtestProperties.slippageBps)
    private val erCalculator = EfficiencyRatioCalculator()
    private val adxCalculator = AdxCalculator()
    private val volExpansionFilter = VolatilityExpansionFilter(properties.volExpansion)

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
        if (!passesTrendStrengthFilter(htfCandles)) {
            return hold(RejectReason.TREND_STRENGTH_FILTER, features)
        }
        if (!passesVolatilityFilter(candles)) {
            return hold(RejectReason.VOLATILITY_FILTER, features)
        }
        if (properties.features.enableVolumeFilter && !passesVolumeFilter(candles)) {
            return hold(RejectReason.VOLUME_FILTER, features)
        }
        if (!passesVolExpansionFilter(candles)) {
            return hold(RejectReason.VOL_EXPANSION_FILTER, features)
        }

        if (properties.features.entryModel == EntryModel.FAST_BREAKOUT) {
            return evaluateFastBreakout(candles, htfCandles, features, regimeGate)
        }

        return if (properties.features.enableWaveFilter) {
            val waveSignal = evaluateWave(candles, htfCandles, features, regimeGate)
            if (properties.features.enableSwingFallback &&
                waveSignal.type == SignalType.HOLD &&
                waveSignal.rejectReason == RejectReason.NO_SETUP
            ) {
                evaluateSwingBreak(candles, features, regimeGate, htfCandles)
            } else {
                waveSignal
            }
        } else {
            evaluateSwingBreak(candles, features, regimeGate, htfCandles)
        }
    }

    private fun evaluateWave(
        candles: List<Candle>,
        htfCandles: List<Candle>,
        features: RegimeFeatures?,
        regimeGate: RegimeGate?,
    ): TradeSignal {
        val swings = zigZagExtractor.extract(candles)
        val wave2Long = detector.findWave2Setup(swings)
        val wave2Short = if (properties.features.enableShortWave) {
            detector.findWave2SetupDown(swings)
        } else {
            null
        }
        if (wave2Long == null && wave2Short == null) return hold(RejectReason.NO_SETUP, features)

        val atrValue = atrCalculator.calculate(candles, properties.volatility.atrPeriod)
            .lastOrNull { it != null }
        val lastClose = candles.last().close
        val longSignal = wave2Long?.let { buildWaveSignal(it, true, candles, htfCandles, features, atrValue, lastClose, regimeGate) }
        val shortSignal = wave2Short?.let { buildWaveSignal(it, false, candles, htfCandles, features, atrValue, lastClose, regimeGate) }

        val candidates = listOfNotNull(longSignal, shortSignal).filter { it.type != SignalType.HOLD }
        if (candidates.isEmpty()) {
            val fallback = longSignal ?: shortSignal
            return hold(fallback?.rejectReason ?: RejectReason.NO_SETUP, features, fallback?.score, fallback?.confidence)
        }
        return candidates.maxByOrNull { it.confidence ?: it.score ?: BigDecimal.ZERO }
            ?: hold(RejectReason.NO_SETUP, features)
    }

    private fun evaluateFastBreakout(
        candles: List<Candle>,
        htfCandles: List<Candle>,
        features: RegimeFeatures?,
        regimeGate: RegimeGate?,
    ): TradeSignal {
        val lookback = properties.fastBreakout.lookbackBars
        if (candles.size <= lookback) return hold(RejectReason.NO_SETUP, features)
        val window = candles.subList(candles.size - lookback - 1, candles.size - 1)
        val maxHigh = window.maxOf { it.high }
        val minLow = window.minOf { it.low }
        val lastClose = candles.last().close
        val atrValue = atrCalculator.calculate(candles, properties.volatility.atrPeriod)
            .lastOrNull { it != null } ?: return hold(RejectReason.NO_SETUP, features)

        if (lastClose > maxHigh) {
            if (properties.features.enableRegimeGate && features != null && regimeGate != null) {
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
            val stop = lastClose.subtract(atrValue.multiply(properties.fastBreakout.atrStopMultiplier))
            val takeProfit = lastClose.add(atrValue.multiply(properties.fastBreakout.atrTakeProfitMultiplier))
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = stop,
                takeProfitCandidate = takeProfit,
                atrValue = atrValue,
                isLong = true,
            )
            if (!feeAwareGate.passes(lastClose, exitPlan.takeProfitPrice)) {
                return hold(RejectReason.FEE_EDGE_FILTER, features)
            }
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
                entryReason = EntryReason.FAST_BREAKOUT,
                features = features,
            )
        }

        if (lastClose < minLow) {
            if (!passesShortGate(features, htfCandles)) {
                return hold(RejectReason.SHORT_GATE, features)
            }
            val stop = lastClose.add(atrValue.multiply(properties.fastBreakout.atrStopMultiplier))
            val takeProfit = lastClose.subtract(atrValue.multiply(properties.fastBreakout.atrTakeProfitMultiplier))
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = stop,
                takeProfitCandidate = takeProfit,
                atrValue = atrValue,
                isLong = false,
            )
            if (!feeAwareGate.passes(lastClose, exitPlan.takeProfitPrice)) {
                return hold(RejectReason.FEE_EDGE_FILTER, features)
            }
            if (isStopTooWide(lastClose, exitPlan.stopPrice, atrValue)) {
                return hold(RejectReason.STOP_DISTANCE, features)
            }
            if (isRewardRiskTooLow(lastClose, exitPlan)) {
                return hold(RejectReason.LOW_REWARD_RISK, features)
            }
            return TradeSignal(
                type = SignalType.ENTER_SHORT,
                entryPrice = lastClose,
                exitPlan = exitPlan,
                score = null,
                confidence = null,
                entryReason = EntryReason.FAST_BREAKOUT,
                features = features,
            )
        }

        return hold(RejectReason.NO_SETUP, features)
    }

    private fun buildWaveSignal(
        setup: Wave2Setup,
        isLong: Boolean,
        candles: List<Candle>,
        htfCandles: List<Candle>,
        features: RegimeFeatures?,
        atrValue: BigDecimal?,
        lastClose: BigDecimal,
        regimeGate: RegimeGate?,
    ): TradeSignal {
        if (isLong) {
            if (properties.features.enableRegimeGate && features != null && regimeGate != null) {
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
        } else {
            if (!passesShortGate(features, htfCandles)) {
                return hold(RejectReason.SHORT_GATE, features)
            }
        }

        val score = scorer.scoreWave2(setup, candles, htfCandles, properties.elliott, properties.volume, isLong)
        val confidence = scorer.confidenceScore(
            setup = setup,
            candles = candles,
            htfCandles = htfCandles,
            elliott = properties.elliott,
            volume = properties.volume,
            atrValue = atrValue,
            isLong = isLong,
        )
        val threshold = properties.elliott.minScoreToTrade
        val prev = candles[candles.size - 2]
        val entryOk = when (properties.features.entryModel) {
            EntryModel.BASELINE -> score >= threshold
            EntryModel.CONFIDENCE_THRESHOLD -> confidence >= threshold
            EntryModel.MOMENTUM_CONFIRM -> if (isLong) {
                score >= threshold && lastClose > prev.high
            } else {
                score >= threshold && lastClose < prev.low
            }
            EntryModel.RELAXED -> true
            EntryModel.FAST_BREAKOUT -> true
        }
        if (!entryOk) return hold(RejectReason.LOW_SCORE, features, score, confidence)

        val triggerOk = if (isLong) lastClose > setup.wave1End.price else lastClose < setup.wave1End.price
        if (!triggerOk) return hold(RejectReason.NO_SETUP, features, score, confidence)

        val wave1Size = setup.wave1End.price.subtract(setup.wave1Start.price).abs()
        val fixedTakeProfit = if (isLong) {
            setup.wave1End.price.add(wave1Size.multiply(properties.elliott.fib.takeProfitExtension))
        } else {
            setup.wave1End.price.subtract(wave1Size.multiply(properties.elliott.fib.takeProfitExtension))
        }
        val exitPlan = exitPlanBuilder.build(
            entryPrice = lastClose,
            stopCandidate = setup.wave1Start.price,
            takeProfitCandidate = fixedTakeProfit,
            atrValue = atrValue,
            isLong = isLong,
        )
        if (!feeAwareGate.passes(lastClose, exitPlan.takeProfitPrice)) {
            return hold(RejectReason.FEE_EDGE_FILTER, features, score, confidence)
        }
        if (isStopTooWide(lastClose, exitPlan.stopPrice, atrValue)) {
            return hold(RejectReason.STOP_DISTANCE, features, score, confidence)
        }
        if (isRewardRiskTooLow(lastClose, exitPlan)) {
            return hold(RejectReason.LOW_REWARD_RISK, features, score, confidence)
        }
        return TradeSignal(
            type = if (isLong) SignalType.ENTER_LONG else SignalType.ENTER_SHORT,
            entryPrice = lastClose,
            exitPlan = exitPlan,
            score = score,
            confidence = confidence,
            entryReason = EntryReason.W2_COMPLETE_BREAK_W1_END,
            features = features,
        )
    }

    private fun evaluateSwingBreak(candles: List<Candle>, features: RegimeFeatures?): TradeSignal {
        return evaluateSwingBreak(candles, features, null, emptyList())
    }

    private fun evaluateSwingBreak(
        candles: List<Candle>,
        features: RegimeFeatures?,
        regimeGate: RegimeGate?,
        htfCandles: List<Candle>,
    ): TradeSignal {
        val swings = zigZagExtractor.extract(candles)
        val lastHigh = swings.lastOrNull { it.type == SwingType.HIGH }
        val lastLow = swings.lastOrNull { it.type == SwingType.LOW }
        if (lastHigh == null || lastLow == null) return hold(RejectReason.NO_SETUP, features)

        val lastClose = candles.last().close
        val prev = candles[candles.size - 2]
        val atrValue = atrCalculator.calculate(candles, properties.volatility.atrPeriod)
            .lastOrNull { it != null }

        if (lastClose > lastHigh.price) {
            if (properties.features.enableRegimeGate && features != null && regimeGate != null) {
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
            val entryOk = when (properties.features.entryModel) {
                EntryModel.MOMENTUM_CONFIRM -> lastClose > prev.high
                else -> true
            }
            if (!entryOk) return hold(RejectReason.LOW_SCORE, features)

            val stop = lastLow.price
            val takeProfit = lastClose.add(
                lastClose.subtract(stop).multiply(properties.elliott.fib.takeProfitExtension)
            )
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = stop,
                takeProfitCandidate = takeProfit,
                atrValue = atrValue,
                isLong = true,
            )
            if (!feeAwareGate.passes(lastClose, exitPlan.takeProfitPrice)) {
                return hold(RejectReason.FEE_EDGE_FILTER, features)
            }
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

        if (lastClose < lastLow.price) {
            if (!passesShortGate(features, htfCandles)) {
                return hold(RejectReason.SHORT_GATE, features)
            }
            val entryOk = when (properties.features.entryModel) {
                EntryModel.MOMENTUM_CONFIRM -> lastClose < prev.low
                else -> true
            }
            if (!entryOk) return hold(RejectReason.LOW_SCORE, features)

            val stop = lastHigh.price
            val riskDistance = stop.subtract(lastClose)
            val takeProfit = lastClose.subtract(
                riskDistance.multiply(properties.elliott.fib.takeProfitExtension)
            )
            val exitPlan = exitPlanBuilder.build(
                entryPrice = lastClose,
                stopCandidate = stop,
                takeProfitCandidate = takeProfit,
                atrValue = atrValue,
                isLong = false,
            )
            if (!feeAwareGate.passes(lastClose, exitPlan.takeProfitPrice)) {
                return hold(RejectReason.FEE_EDGE_FILTER, features)
            }
            if (isStopTooWide(lastClose, exitPlan.stopPrice, atrValue)) {
                return hold(RejectReason.STOP_DISTANCE, features)
            }
            if (isRewardRiskTooLow(lastClose, exitPlan)) {
                return hold(RejectReason.LOW_REWARD_RISK, features)
            }
            return TradeSignal(
                type = SignalType.ENTER_SHORT,
                entryPrice = lastClose,
                exitPlan = exitPlan,
                score = null,
                confidence = null,
                entryReason = EntryReason.SWING_BREAKDOWN,
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

    private fun passesDownTrendFilter(htfCandles: List<Candle>): Boolean {
        if (htfCandles.size < 200) return false
        val sma50 = sma(htfCandles.takeLast(50))
        val sma200 = sma(htfCandles.takeLast(200))
        return sma50 < sma200
    }

    private fun passesShortGate(features: RegimeFeatures?, htfCandles: List<Candle>): Boolean {
        if (!properties.shortGate.enabled) return true
        if (properties.shortGate.requireDowntrend && !passesDownTrendFilter(htfCandles)) return false
        val thresholds = properties.regime.thresholds
        if (thresholds.atrLow <= BigDecimal.ZERO ||
            thresholds.atrHigh <= BigDecimal.ZERO ||
            thresholds.volumeLow <= BigDecimal.ZERO ||
            thresholds.volumeHigh <= BigDecimal.ZERO
        ) {
            return true
        }
        val f = features ?: return false
        val bucket = RegimeBucketer.bucket(
            features = f,
            thresholds = io.github.yaklede.elliott.wave.principle.coin.domain.RegimeThresholds(
                atrLow = thresholds.atrLow,
                atrHigh = thresholds.atrHigh,
                volumeLow = thresholds.volumeLow,
                volumeHigh = thresholds.volumeHigh,
            ),
            weakSlope = properties.regime.weakSlope,
            strongSlope = properties.regime.strongSlope,
        )
        val allowed = properties.shortGate.allowed.mapNotNull { parseBucket(it) }.toSet()
        val blocked = properties.shortGate.blocked.mapNotNull { parseBucket(it) }.toSet()
        if (allowed.isNotEmpty() && bucket !in allowed) return false
        if (blocked.contains(bucket)) return false
        return true
    }

    private fun parseBucket(raw: String): io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketKey? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('|', ',', ';')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        return try {
            io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketKey(
                trend = io.github.yaklede.elliott.wave.principle.coin.domain.TrendBucket.valueOf(parts[0]),
                vol = io.github.yaklede.elliott.wave.principle.coin.domain.VolBucket.valueOf(parts[1]),
                volume = io.github.yaklede.elliott.wave.principle.coin.domain.VolumeBucket.valueOf(parts[2]),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun passesTrendStrengthFilter(htfCandles: List<Candle>): Boolean {
        if (!properties.trendStrength.enabled) return true
        if (htfCandles.isEmpty()) return true
        val threshold = properties.trendStrength.threshold
        val value = when (properties.trendStrength.model) {
            TrendStrengthModel.ER -> erCalculator.compute(htfCandles, properties.trendStrength.n)
            TrendStrengthModel.ADX -> adxCalculator.compute(htfCandles, properties.trendStrength.n)
        } ?: return true
        return value >= threshold
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

    private fun passesVolExpansionFilter(candles: List<Candle>): Boolean = volExpansionFilter.passes(candles)

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

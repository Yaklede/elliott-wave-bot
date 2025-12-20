package io.github.yaklede.elliott.wave.principle.coin.domain

import java.math.BigDecimal

object RegimeBucketer {
    fun bucket(
        features: RegimeFeatures,
        thresholds: RegimeThresholds,
        weakSlope: BigDecimal,
        strongSlope: BigDecimal,
    ): RegimeBucketKey {
        val slope = features.trendSlope ?: BigDecimal.ZERO
        val maSpread = features.maSpread ?: BigDecimal.ZERO
        val trend = when {
            maSpread >= BigDecimal.ZERO && slope >= strongSlope -> TrendBucket.UP_STRONG
            maSpread >= BigDecimal.ZERO && slope >= weakSlope -> TrendBucket.UP_WEAK
            maSpread <= BigDecimal.ZERO && slope <= strongSlope.negate() -> TrendBucket.DOWN_STRONG
            maSpread <= BigDecimal.ZERO && slope <= weakSlope.negate() -> TrendBucket.DOWN_WEAK
            else -> TrendBucket.FLAT
        }

        val atr = features.atrPercent ?: BigDecimal.ZERO
        val vol = when {
            atr < thresholds.atrLow -> VolBucket.LOW
            atr < thresholds.atrHigh -> VolBucket.MID
            else -> VolBucket.HIGH
        }

        val relVol = features.relVolume ?: BigDecimal.ZERO
        val volume = when {
            relVol < thresholds.volumeLow -> VolumeBucket.LOW
            relVol < thresholds.volumeHigh -> VolumeBucket.NORMAL
            else -> VolumeBucket.HIGH
        }

        return RegimeBucketKey(trend = trend, vol = vol, volume = volume)
    }
}

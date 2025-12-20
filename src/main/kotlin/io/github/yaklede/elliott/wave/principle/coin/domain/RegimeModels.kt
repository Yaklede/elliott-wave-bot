package io.github.yaklede.elliott.wave.principle.coin.domain

import java.math.BigDecimal

data class RegimeFeatures(
    val trendSlope: BigDecimal?,
    val maSpread: BigDecimal?,
    val atrPercent: BigDecimal?,
    val relVolume: BigDecimal?,
)

enum class TrendBucket {
    UP_STRONG,
    UP_WEAK,
    FLAT,
    DOWN_WEAK,
    DOWN_STRONG,
}

enum class VolBucket {
    LOW,
    MID,
    HIGH,
}

enum class VolumeBucket {
    LOW,
    NORMAL,
    HIGH,
}

data class RegimeBucketKey(
    val trend: TrendBucket,
    val vol: VolBucket,
    val volume: VolumeBucket,
)

data class RegimeThresholds(
    val atrLow: BigDecimal,
    val atrHigh: BigDecimal,
    val volumeLow: BigDecimal,
    val volumeHigh: BigDecimal,
)

data class RegimeGate(
    val thresholds: RegimeThresholds,
    val blockedBuckets: Set<RegimeBucketKey>,
    val minTradesPerBucket: Int,
    val allowedBuckets: Set<RegimeBucketKey> = emptySet(),
) {
    fun isBlocked(bucket: RegimeBucketKey): Boolean {
        if (allowedBuckets.isNotEmpty() && bucket !in allowedBuckets) return true
        return blockedBuckets.contains(bucket)
    }
}

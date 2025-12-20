package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketer
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeThresholds
import io.github.yaklede.elliott.wave.principle.coin.domain.TrendBucket
import io.github.yaklede.elliott.wave.principle.coin.domain.VolBucket
import io.github.yaklede.elliott.wave.principle.coin.domain.VolumeBucket
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegimeBucketerTest {
    @Test
    fun `regime bucketing is deterministic`() {
        val features = RegimeFeatures(
            trendSlope = BigDecimal("0.02"),
            maSpread = BigDecimal("0.01"),
            atrPercent = BigDecimal("0.01"),
            relVolume = BigDecimal("1.5"),
        )
        val thresholds = RegimeThresholds(
            atrLow = BigDecimal("0.005"),
            atrHigh = BigDecimal("0.015"),
            volumeLow = BigDecimal("0.8"),
            volumeHigh = BigDecimal("1.2"),
        )
        val bucket = RegimeBucketer.bucket(features, thresholds, BigDecimal("0.002"), BigDecimal("0.01"))
        assertEquals(TrendBucket.UP_STRONG, bucket.trend)
        assertEquals(VolBucket.MID, bucket.vol)
        assertEquals(VolumeBucket.HIGH, bucket.volume)
    }
}

package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeBucketKey
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeGate
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeThresholds
import io.github.yaklede.elliott.wave.principle.coin.domain.TrendBucket
import io.github.yaklede.elliott.wave.principle.coin.domain.VolBucket
import io.github.yaklede.elliott.wave.principle.coin.domain.VolumeBucket
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class RegimeGateProvider(
    private val strategyProperties: StrategyProperties,
) {
    fun currentGate(): RegimeGate? {
        if (!strategyProperties.features.enableRegimeGate) return null
        val thresholds = thresholdsOrNull() ?: return null
        val blocked = strategyProperties.regime.blocked.mapNotNull { parseBucket(it) }.toSet()
        if (blocked.isEmpty()) return null
        return RegimeGate(
            thresholds = thresholds,
            blockedBuckets = blocked,
            minTradesPerBucket = strategyProperties.regime.minTradesPerBucket,
        )
    }

    private fun thresholdsOrNull(): RegimeThresholds? {
        val t = strategyProperties.regime.thresholds
        if (t.atrLow <= BigDecimal.ZERO || t.atrHigh <= BigDecimal.ZERO) return null
        if (t.volumeLow <= BigDecimal.ZERO || t.volumeHigh <= BigDecimal.ZERO) return null
        return RegimeThresholds(
            atrLow = t.atrLow,
            atrHigh = t.atrHigh,
            volumeLow = t.volumeLow,
            volumeHigh = t.volumeHigh,
        )
    }

    private fun parseBucket(raw: String): RegimeBucketKey? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('|', ',', ';')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        return try {
            RegimeBucketKey(
                trend = TrendBucket.valueOf(parts[0]),
                vol = VolBucket.valueOf(parts[1]),
                volume = VolumeBucket.valueOf(parts[2]),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.RegimeThresholdProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyFeatures
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class RegimeGateProviderTest {
    @Test
    fun `provider builds gate from config`() {
        val props = StrategyProperties(
            features = StrategyFeatures(enableRegimeGate = true),
            regime = StrategyProperties().regime.copy(
                thresholds = RegimeThresholdProperties(
                    atrLow = BigDecimal("0.002"),
                    atrHigh = BigDecimal("0.004"),
                    volumeLow = BigDecimal("1.2"),
                    volumeHigh = BigDecimal("1.8"),
                ),
                blocked = listOf("UP_STRONG|HIGH|HIGH"),
            ),
        )
        val provider = RegimeGateProvider(props)
        val gate = provider.currentGate()
        assertNotNull(gate)
        assertEquals(1, gate!!.blockedBuckets.size)
    }
}

package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.FeeAwareProperties
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeeAwareGateTest {
    @Test
    fun `blocks entries when expected move is below cost threshold`() {
        val props = FeeAwareProperties(enabled = true, minEdgeMultiple = BigDecimal("2.0"), bufferBps = BigDecimal("1"))
        val gate = FeeAwareGate(props, feeRate = BigDecimal("0.001"), slippageBps = 2)
        val entry = BigDecimal("100")
        val tp = BigDecimal("100.1") // 10 bps expected move
        assertFalse(gate.passes(entry, tp))
    }

    @Test
    fun `allows entries when expected move exceeds cost threshold`() {
        val props = FeeAwareProperties(enabled = true, minEdgeMultiple = BigDecimal("1.5"), bufferBps = BigDecimal("1"))
        val gate = FeeAwareGate(props, feeRate = BigDecimal("0.0001"), slippageBps = 1)
        val entry = BigDecimal("100")
        val tp = BigDecimal("102.0") // 200 bps expected move
        assertTrue(gate.passes(entry, tp))
    }
}

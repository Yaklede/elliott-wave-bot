package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.FeeAwareProperties
import java.math.BigDecimal
import java.math.RoundingMode

class FeeAwareGate(
    private val properties: FeeAwareProperties,
    private val feeRate: BigDecimal,
    private val slippageBps: Int,
) {
    fun passes(entryPrice: BigDecimal, takeProfitPrice: BigDecimal?): Boolean {
        if (!properties.enabled) return true
        if (takeProfitPrice == null) return true
        if (entryPrice <= BigDecimal.ZERO) return true

        val expectedMoveBps = takeProfitPrice.subtract(entryPrice).abs()
            .divide(entryPrice, 8, RoundingMode.HALF_UP)
            .multiply(BPS)
        val feeBpsRoundTrip = feeRate.multiply(BPS).multiply(BigDecimal("2"))
        val slippageRoundTrip = BigDecimal(slippageBps * 2)
        val costBps = feeBpsRoundTrip.add(slippageRoundTrip).add(properties.bufferBps)
        val minEdge = costBps.multiply(properties.minEdgeMultiple)
        return expectedMoveBps >= minEdge
    }

    companion object {
        private val BPS = BigDecimal("10000")
    }
}
